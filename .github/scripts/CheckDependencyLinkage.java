import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javassist.*;
import javassist.bytecode.*;

/** Checks symbolic references, without loading or executing application classes.
 * Javassist is supplied by the checksum-pinned japicmp distribution, not a new download.
 */
public class CheckDependencyLinkage {
    record Input(String side, String label, Path path) {}
    record Reference(String owner, String name, String descriptor, boolean field, Boolean isStatic) {}
    record BytecodeReferences(Set<Reference> members, Set<String> instantiations) {}
    record Member(CtClass owner, int flags) {}
    static final Pattern DESCRIPTOR = Pattern.compile("L([^;]+);");
    final ClassPool oldPool = new ClassPool(false);
    final ClassPool newPool = new ClassPool(false);
    final Set<String> changed = new HashSet<>();
    final Set<String> externalClasses = new HashSet<>();
    final Map<String, Boolean> affected = new HashMap<>();
    final Map<String, String> externalOrigins = new HashMap<>();
    final Map<String, BytecodeReferences> oldReferences = new HashMap<>();
    final Map<String, Set<String>> oldTypes = new HashMap<>();
    final Set<String> visited = new HashSet<>();
    final Set<String> findings = new TreeSet<>();
    final Set<String> incomplete = new TreeSet<>();
    final Set<String> observed = new TreeSet<>();
    long references;
    long relevantReferences;

    interface ClassReader { void read(ClassFile file) throws Exception; }

    static void classes(Path path, ClassReader reader) throws Exception {
        if (!Files.exists(path)) throw new IOException("Missing class input: " + path);
        if (Files.isDirectory(path)) {
            try (var files = Files.walk(path)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                    try (var input = new DataInputStream(Files.newInputStream(file))) {
                        reader.read(new ClassFile(input));
                    }
                }
            }
        } else {
            try (var zip = new ZipFile(path.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    // Android consumes the base classes, not JVM multi-release alternatives.
                    if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")
                            || entry.getName().equals("module-info.class")) continue;
                    try (var input = new DataInputStream(zip.getInputStream(entry))) {
                        reader.read(new ClassFile(input));
                    }
                }
            }
        }
    }

    boolean affected(String name, Set<String> seen) throws NotFoundException {
        if (changed.contains(name)) return true;
        if (affected.containsKey(name)) return affected.get(name);
        if (!seen.add(name)) return false;
        CtClass type = oldPool.getOrNull(name);
        if (type == null) return false; // Optional dependencies outside the changed graph.
        if (type.isArray() || type.isPrimitive()) return false;
        ClassFile file = type.getClassFile2();
        boolean result = false;
        if (file.getSuperclass() != null) result = affected(file.getSuperclass(), seen);
        for (String parent : file.getInterfaces()) result |= affected(parent, seen);
        affected.put(name, result);
        return result;
    }

    Member member(ClassPool pool, String owner, Reference ref, Set<String> seen, Set<String> missing)
            throws NotFoundException {
        if (!seen.add(owner)) return null;
        CtClass type = pool.getOrNull(owner);
        if (type == null) {
            missing.add(owner);
            return null;
        }
        ClassFile file = type.getClassFile2();
        if (ref.field()) {
            for (FieldInfo field : file.getFields()) {
                if (field.getName().equals(ref.name()) && field.getDescriptor().equals(ref.descriptor()))
                    return new Member(type, field.getAccessFlags());
            }
        } else {
            for (MethodInfo method : file.getMethods()) {
                if (method.getName().equals(ref.name()) && method.getDescriptor().equals(ref.descriptor()))
                    return new Member(type, method.getAccessFlags());
            }
            if (ref.name().equals("<init>")) return null;
        }
        // Field resolution searches superinterfaces before the superclass (JVMS 5.4.3.2).
        if (!ref.field() && file.getSuperclass() != null) {
            Member result = member(pool, file.getSuperclass(), ref, seen, missing);
            if (result != null) return result;
        }
        for (String parent : file.getInterfaces()) {
            Member result = member(pool, parent, ref, seen, missing);
            if (result != null) return result;
        }
        return ref.field() && file.getSuperclass() != null
            ? member(pool, file.getSuperclass(), ref, seen, missing) : null;
    }

    static String packageName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot);
    }

    boolean accessible(Member member, String caller, ClassPool pool) throws NotFoundException {
        int flags = member.flags();
        String owner = member.owner().getName();
        if (Modifier.isPublic(flags) || caller.equals(owner)) return true;
        if (Modifier.isPrivate(flags)) return nestHost(pool.get(caller)).equals(nestHost(member.owner()));
        if (packageName(caller).equals(packageName(owner))) return true;
        return Modifier.isProtected(flags) && pool.get(caller).subtypeOf(member.owner());
    }

    static String nestHost(CtClass type) {
        var file = type.getClassFile2();
        var attribute = (NestHostAttribute) file.getAttribute(NestHostAttribute.tag);
        return attribute == null ? type.getName() : file.getConstPool().getClassInfo(attribute.hostClassIndex());
    }

    void finding(String caller, String symbol, String reason) {
        findings.add(caller + "\t" + symbol + "\t" + reason);
    }

    void reference(String caller, String className, Reference ref) throws Exception {
        references++;
        boolean changedConsumer = changed.contains(className);
        if (!affected(ref.owner(), new HashSet<>()) && !changedConsumer) return;
        relevantReferences++;
        boolean introducedReference = changedConsumer && !oldReferences.get(className).members().contains(ref);
        var missingOld = new TreeSet<String>();
        var before = member(oldPool, ref.owner(), ref, new HashSet<>(), missingOld);
        var missingNew = new TreeSet<String>();
        var after = member(newPool, ref.owner(), ref, new HashSet<>(), missingNew);
        String symbol = ref.owner() + "#" + ref.name() + ref.descriptor();
        observed.add(caller + "\t" + symbol + "\t" + (after != null ? "resolves-in-candidate"
            : before == null ? "unresolved-in-both-graphs" : "regression"));
        if (before == null && !introducedReference) return; // Pre-existing unresolved reference.
        if (after == null) {
            if (!missingNew.isEmpty() && newPool.getOrNull(ref.owner()) != null) {
                incomplete.add(symbol + ": cannot resolve hierarchy " + missingNew);
            } else {
                finding(caller, symbol, before == null ? "updated consumer introduces an unresolved reference"
                    : "previously resolved member is missing");
            }
        } else if (ref.isStatic() != null && (introducedReference || before != null && Modifier.isStatic(before.flags()) == ref.isStatic())
                && Modifier.isStatic(after.flags()) != ref.isStatic()) {
            finding(caller, symbol, "static/instance linkage changed");
        } else if ((introducedReference || before != null && accessible(before, className, oldPool))
                && !accessible(after, className, newPool)) {
            finding(caller, symbol, "previously accessible member is no longer accessible");
        }
    }

    static Reference reference(ConstPool cp, int index, Boolean isStatic) {
        return switch (cp.getTag(index)) {
            case ConstPool.CONST_Methodref -> new Reference(cp.getMethodrefClassName(index),
                cp.getMethodrefName(index), cp.getMethodrefType(index), false, isStatic);
            case ConstPool.CONST_InterfaceMethodref -> new Reference(cp.getInterfaceMethodrefClassName(index),
                cp.getInterfaceMethodrefName(index), cp.getInterfaceMethodrefType(index), false, isStatic);
            case ConstPool.CONST_Fieldref -> new Reference(cp.getFieldrefClassName(index),
                cp.getFieldrefName(index), cp.getFieldrefType(index), true, isStatic);
            default -> null;
        };
    }

    static void descriptorTypes(String descriptor, Set<String> types) {
        var matcher = DESCRIPTOR.matcher(descriptor);
        while (matcher.find()) types.add(matcher.group(1).replace('/', '.'));
    }

    static BytecodeReferences bytecodeReferences(ClassFile file) throws Exception {
        var cp = file.getConstPool();
        var refs = new HashSet<Reference>();
        var instantiations = new HashSet<String>();
        for (int i = 1; i < cp.getSize(); i++) {
            Reference ref = reference(cp, i, null);
            if (ref != null) refs.add(ref);
            if (cp.getTag(i) == ConstPool.CONST_MethodHandle) {
                int kind = cp.getMethodHandleKind(i);
                Reference target = reference(cp, cp.getMethodHandleIndex(i), kind == ConstPool.REF_invokeStatic
                    || kind == ConstPool.REF_getStatic || kind == ConstPool.REF_putStatic);
                refs.add(target);
                if (kind == ConstPool.REF_newInvokeSpecial && target != null) instantiations.add(target.owner());
            }
        }
        for (MethodInfo method : file.getMethods()) {
            if (method.getCodeAttribute() == null) continue;
            var code = method.getCodeAttribute().iterator();
            while (code.hasNext()) {
                int offset = code.next();
                int opcode = code.byteAt(offset);
                if (opcode >= Opcode.GETSTATIC && opcode <= Opcode.INVOKEINTERFACE)
                    refs.add(reference(cp, code.u16bitAt(offset + 1),
                        opcode == Opcode.GETSTATIC || opcode == Opcode.PUTSTATIC || opcode == Opcode.INVOKESTATIC));
                if (opcode == Opcode.NEW) instantiations.add(cp.getClassInfo(code.u16bitAt(offset + 1)));
            }
        }
        refs.remove(null);
        return new BytecodeReferences(refs, instantiations);
    }

    static Set<String> referencedTypes(ClassFile file) {
        var types = new HashSet<String>();
        var cp = file.getConstPool();
        for (int i = 1; i < cp.getSize(); i++) {
            if (cp.getTag(i) == ConstPool.CONST_Class) {
                String name = cp.getClassInfo(i);
                if (name.startsWith("[")) descriptorTypes(name, types); else types.add(name);
            } else if (cp.getTag(i) == ConstPool.CONST_NameAndType)
                descriptorTypes(cp.getUtf8Info(cp.getNameAndTypeDescriptor(i)), types);
        }
        for (FieldInfo field : file.getFields()) descriptorTypes(field.getDescriptor(), types);
        for (MethodInfo method : file.getMethods()) descriptorTypes(method.getDescriptor(), types);
        return types;
    }

    void scan(String origin, ClassFile file) throws Exception {
        if (!visited.add(file.getName())) return;
        String caller = externalOrigins.getOrDefault(file.getName(), origin) + "!" + file.getName();
        boolean changedConsumer = changed.contains(file.getName());
        BytecodeReferences usage = bytecodeReferences(file);
        if (changedConsumer) {
            CtClass before = oldPool.getOrNull(file.getName());
            oldReferences.put(file.getName(), before == null
                ? new BytecodeReferences(Set.of(), Set.of()) : bytecodeReferences(before.getClassFile2()));
            oldTypes.put(file.getName(), before == null ? Set.of() : referencedTypes(before.getClassFile2()));
        }
        for (String type : referencedTypes(file)) {
            if (!changed.contains(type) && !changedConsumer) continue;
            CtClass before = oldPool.getOrNull(type);
            CtClass after = newPool.getOrNull(type);
            boolean wasConcrete = before != null && !Modifier.isAbstract(before.getModifiers());
            boolean brokenInstantiation = usage.instantiations().contains(type)
                && after != null && !after.isInterface() && Modifier.isAbstract(after.getModifiers())
                && (wasConcrete || changedConsumer && !oldReferences.get(file.getName()).instantiations().contains(type));
            relevantReferences++;
            String resolution = after != null ? "resolves-in-candidate"
                : before == null ? "unresolved-in-both-graphs" : "regression";
            observed.add(caller + "\t" + type + "\t" + (brokenInstantiation ? "regression" : resolution));
            if (brokenInstantiation)
                finding(caller, type, wasConcrete ? "instantiated class became abstract"
                    : "updated consumer instantiates an abstract class");
            if (before != null && after == null) finding(caller, type, "previously resolved class is missing");
            if (before == null && after == null && changedConsumer && !oldTypes.get(file.getName()).contains(type))
                finding(caller, type, "updated consumer introduces an unresolved class reference");
            if (before != null && after != null) {
                if (before.isInterface() != after.isInterface()) finding(caller, type, "class/interface kind changed");
                if (Modifier.isPublic(before.getModifiers()) && !Modifier.isPublic(after.getModifiers())
                        && !packageName(type).equals(packageName(file.getName())))
                    finding(caller, type, "class is no longer public");
            }
        }
        for (Reference ref : usage.members()) reference(caller, file.getName(), ref);
        checkInheritance(caller, file);
    }

    void checkInheritance(String caller, ClassFile file) throws Exception {
        if (!affected(file.getName(), new HashSet<>()) || file.isInterface()) return;
        String parent = file.getSuperclass();
        CtClass oldParent = parent == null ? null : oldPool.getOrNull(parent);
        CtClass newParent = parent == null ? null : newPool.getOrNull(parent);
        if (oldParent != null && newParent != null && !Modifier.isFinal(oldParent.getModifiers())
                && Modifier.isFinal(newParent.getModifiers())) finding(caller, parent, "superclass became final");
        for (MethodInfo method : file.getMethods()) {
            if (parent == null || method.getName().startsWith("<") || Modifier.isStatic(method.getAccessFlags())) continue;
            var ref = new Reference(parent, method.getName(), method.getDescriptor(), false, false);
            var before = member(oldPool, parent, ref, new HashSet<>(), new HashSet<>());
            var after = member(newPool, parent, ref, new HashSet<>(), new HashSet<>());
            if (before != null && after != null && !Modifier.isFinal(before.flags()) && Modifier.isFinal(after.flags())
                    && accessible(after, file.getName(), newPool))
                finding(caller, parent + "#" + method.getName() + method.getDescriptor(), "overridden method became final");
        }
        if (Modifier.isAbstract(file.getAccessFlags())) return;
        checkAbstractMethods(caller, file.getName(), file.getName(), new HashSet<>());
    }

    void checkAbstractMethods(String caller, String implementation, String name, Set<String> seen) throws Exception {
        if (!seen.add(name)) return;
        CtClass type = newPool.getOrNull(name);
        if (type == null) return;
        ClassFile file = type.getClassFile2();
        for (MethodInfo method : file.getMethods()) {
            if (!Modifier.isAbstract(method.getAccessFlags()) || !changed.contains(name)) continue;
            var ref = new Reference(implementation, method.getName(), method.getDescriptor(), false, false);
            var after = member(newPool, implementation, ref, new HashSet<>(), new HashSet<>());
            var before = member(oldPool, implementation, ref, new HashSet<>(), new HashSet<>());
            var oldRequirement = member(oldPool, name, ref, new HashSet<>(), new HashSet<>());
            boolean newRequirement = oldRequirement == null || !Modifier.isAbstract(oldRequirement.flags());
            if (!implementsMethod(after, method.getAccessFlags())
                    && (newRequirement || implementsMethod(before, method.getAccessFlags())))
                finding(caller, name + "#" + method.getName() + method.getDescriptor(), "concrete consumer lacks new abstract implementation");
        }
        if (file.getSuperclass() != null) checkAbstractMethods(caller, implementation, file.getSuperclass(), seen);
        for (String parent : file.getInterfaces()) checkAbstractMethods(caller, implementation, parent, seen);
    }

    static boolean implementsMethod(Member member, int requiredFlags) {
        return member != null && !Modifier.isAbstract(member.flags()) && !Modifier.isStatic(member.flags())
            && !Modifier.isPrivate(member.flags())
            && (!Modifier.isPublic(requiredFlags) || Modifier.isPublic(member.flags()));
    }

    int run(Path manifest, Path report) throws Exception {
        var inputs = new ArrayList<Input>();
        for (String row : Files.readAllLines(manifest)) {
            String[] parts = row.split("\t", -1);
            if (parts.length != 3) throw new IOException("Invalid linkage input: " + row);
            Path path = Path.of(parts[2]);
            if (!Files.exists(path)) throw new IOException("Missing linkage input: " + path);
            inputs.add(new Input(parts[0], parts[1], path));
        }
        // Old/new external artifacts must precede the candidate's scoped dependency copies.
        for (Input input : inputs) {
            if (input.side().equals("old")) {
                oldPool.appendClassPath(input.path().toString());
                classes(input.path(), file -> externalClasses.add(file.getName()));
            }
            if (input.side().equals("new")) {
                newPool.appendClassPath(input.path().toString());
                classes(input.path(), file -> {
                    externalClasses.add(file.getName());
                    externalOrigins.put(file.getName(), input.label());
                });
            }
            if (input.side().equals("changed")) classes(input.path(), file -> changed.add(file.getName()));
        }
        var localPool = new ClassPool(false);
        for (Input input : inputs) {
            if (input.side().equals("consumer") || input.side().equals("project"))
                localPool.appendClassPath(input.path().toString());
            if (input.side().equals("platform")) {
                oldPool.appendClassPath(input.path().toString());
                newPool.appendClassPath(input.path().toString());
            }
        }
        // Scoped inputs contain dependency copies too. They must never restore an
        // external symbol removed from this context's candidate runtime graph.
        ClassPath localClasses = new ClassPath() {
            public URL find(String name) {
                return externalClasses.contains(name) ? null : localPool.find(name);
            }
            public InputStream openClassfile(String name) throws NotFoundException {
                URL location = find(name);
                if (location == null) return null;
                try { return location.openStream(); }
                catch (IOException error) { throw new NotFoundException(name, error); }
            }
        };
        oldPool.appendClassPath(localClasses);
        newPool.appendClassPath(localClasses);
        // Only JDK platform classes are fallbacks. The checker's own bundled libraries
        // must not accidentally satisfy a symbol missing from the app's runtime.
        oldPool.appendClassPath(new LoaderClassPath(ClassLoader.getPlatformClassLoader()));
        newPool.appendClassPath(new LoaderClassPath(ClassLoader.getPlatformClassLoader()));
        // Scan selected dependency versions first. Scoped feature inputs can contain a
        // different version of the same library; those copies must not win by path order.
        for (Input input : inputs) if (input.side().equals("new"))
            classes(input.path(), file -> scan(input.label(), file));
        var projectCounts = new TreeMap<String, Integer>();
        for (Input input : inputs) if (input.side().equals("project")) {
            projectCounts.putIfAbsent(input.label(), 0);
            classes(input.path(), file -> {
                projectCounts.merge(input.label(), 1, Integer::sum);
                scan(input.label(), file);
            });
        }
        for (Input input : inputs) if (input.side().equals("consumer"))
            classes(input.path(), file -> {
                if (!externalClasses.contains(file.getName())) scan(input.label(), file);
            });
        if (projectCounts.isEmpty() || projectCounts.containsValue(0))
            throw new IOException("Missing compiled project consumers: " + projectCounts);
        var output = new ArrayList<String>();
        output.add("consumer\tsymbol\trisk");
        output.addAll(findings);
        for (String item : incomplete) output.add("INCOMPLETE\t" + item + "\tunresolved comparison hierarchy");
        Files.write(report, output);
        var usage = new ArrayList<String>();
        usage.add("consumer\tsymbol\tresolution");
        usage.addAll(observed);
        Files.write(report.resolveSibling("referenced-apis.tsv"), usage);
        System.out.printf(Locale.ROOT, "Scanned %,d consumer classes; %,d member references; %,d references checked for update impact.%n",
            visited.size(), references, relevantReferences);
        for (String item : findings) System.out.println("LINKAGE RISK: " + item);
        for (String item : incomplete) System.out.println("INCOMPLETE: " + item);
        System.out.println("Report: " + report);
        System.out.println("Compiled project coverage: " + projectCounts);
        return incomplete.isEmpty() ? (findings.isEmpty() ? 0 : 1) : 2;
    }

    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException("Expected inputs.tsv and report.tsv");
            System.exit(new CheckDependencyLinkage().run(Path.of(args[0]), Path.of(args[1])));
        } catch (Exception error) {
            System.err.println("Linkage analysis could not complete: " + error);
            error.printStackTrace();
            System.exit(2);
        }
    }
}
