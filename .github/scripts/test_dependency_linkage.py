#!/usr/bin/env python3
"""Compile incompatible libraries and consumers; exercise the real bytecode checker."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parent
JAPICMP = Path(os.environ["JAPICMP"]).resolve()


class LinkageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compiled = tempfile.TemporaryDirectory()
        subprocess.run(["javac", "-cp", str(JAPICMP), "-d", cls.compiled.name,
                        str(ROOT / "CheckDependencyLinkage.java")], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.compiled.cleanup()

    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)

    def compile(self, name, sources, classpath=()):
        source_root = self.root / name / "src"
        output = self.root / name / "classes"
        output.mkdir(parents=True)
        files = []
        for path, text in sources.items():
            target = source_root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
            files.append(str(target))
        command = ["javac", "--release", "17", "-d", str(output)]
        if classpath:
            command += ["-cp", os.pathsep.join(map(str, classpath))]
        subprocess.run(command + files, check=True, capture_output=True)
        jar = self.root / f"{name}.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for path in sorted(output.rglob("*.class")):
                archive.write(path, path.relative_to(output))
        return jar

    def fixture(self, old, new, consumer, extra_old=None, extra_new=None):
        old_jar = self.compile("old", {"lib/Api.java": "package lib; " + old})
        new_jar = self.compile("new", {"lib/Api.java": "package lib; " + new})
        extra = []
        if extra_old:
            old_extra = self.compile("old-extra", extra_old, [old_jar])
            new_extra = (self.compile("new-extra", extra_new, [new_jar]) if extra_new else old_extra)
            extra = [("old", "unchanged-dependency", old_extra), ("new", "unchanged-dependency", new_extra),
                     ("consumer", "unchanged-dependency", new_extra)]
        consumer_jar = self.compile("consumer", consumer, [old_jar] + ([old_extra] if extra_old else []))
        rows = [("old", "lib:api:1", old_jar), ("new", "lib:api:2", new_jar),
                ("changed", "lib:api:1", old_jar), ("changed", "lib:api:2", new_jar),
                ("project", ":app/generated-and-project-classes", consumer_jar),
                ("consumer", "lib:api:2", new_jar)] + extra
        manifest = self.root / "inputs.tsv"
        manifest.write_text("".join(f"{kind}\t{label}\t{path}\n" for kind, label, path in rows))
        report = self.root / "risks.tsv"
        result = subprocess.run(["java", "-cp", os.pathsep.join([self.compiled.name, str(JAPICMP)]),
                                 "CheckDependencyLinkage", str(manifest), str(report)],
                                capture_output=True, text=True)
        return result, report.read_text() if report.exists() else result.stderr

    def test_used_removed_method_blocks_with_consumer(self):
        result, report = self.fixture("public class Api { public void used() {} }", "public class Api {}",
            {"app/Client.java": "package app; public class Client { void call() { new lib.Api().used(); } }"})
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("app.Client", report)
        self.assertIn("lib.Api#used()V", report)

    def test_unused_removed_method_and_retained_overload_pass(self):
        result, report = self.fixture(
            "public class Api { public void call() {} public void call(int x) {} }",
            "public class Api { public void call(int x) {} }",
            {"app/Client.java": "package app; public class Client { void call(lib.Api[] xs) { xs.clone(); new lib.Api().call(1); } }"})
        self.assertEqual(0, result.returncode, report + result.stderr)
        self.assertIn("lib.Api#call(I)V", (self.root / "referenced-apis.tsv").read_text())

    def test_full_pipeline_reports_unused_break_without_blocking(self):
        self.fixture("public class Api { public void unused() {} public void kept() {} }",
            "public class Api { public void kept() {} }",
            {"app/Client.java": "package app; public class Client { void call() { new lib.Api().kept(); } }"})
        artifacts = self.root / "artifacts"
        artifacts.mkdir()
        (artifacts / "artifacts.tsv").write_text(
            f"old\tlib:api:1\t{self.root / 'old.jar'}\nnew\tlib:api:2\t{self.root / 'new.jar'}\n")
        (artifacts / "consumers.tsv").write_text(
            f"project\t:app\t{self.root / 'consumer.jar'}\n"
            f"consumer\tlib:api:2\t{self.root / 'new.jar'}\n"
            f"platform\tfixture\t{self.root / 'new.jar'}\n")
        (self.root / "old.txt").write_text("lib:api:1\n")
        (self.root / "new.txt").write_text("lib:api:2\n")
        result = subprocess.run(["python3", "-I", str(ROOT / "check-dependency-api.py"),
            "--old-graph", str(self.root / "old.txt"), "--new-graph", str(self.root / "new.txt"),
            "--artifacts", str(artifacts), "--japicmp", str(JAPICMP)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("unused", (artifacts / "reports/lib-api.xml").read_text())
        self.assertIn("kept", (artifacts / "reports/referenced-apis.tsv").read_text())
        self.assertNotIn("unused", (artifacts / "reports/linkage-risks.tsv").read_text())

    def test_precompiled_transitive_consumer_blocks(self):
        result, report = self.fixture("public class Api { public void internal() {} }", "public class Api {}",
            {"app/Client.java": "package app; public class Client { public void call() { new other.Adapter().call(); } }"},
            {"other/Adapter.java": "package other; public class Adapter { public void call() { new lib.Api().internal(); } }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("unchanged-dependency!other.Adapter", report)

    def test_generated_serializer_reference_blocks(self):
        result, report = self.fixture("public class Api { public void internal() {} }", "public class Api {}",
            {"app/Dto$$serializer.java": "package app; public class Dto$$serializer { void call() { new lib.Api().internal(); } }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("Dto$$serializer", report)

    def test_inherited_method_reference_blocks(self):
        result, report = self.fixture("public class Api { public void used() {} }", "public class Api {}",
            {"app/Client.java": "package app; public class Client { void call() { new other.Child().used(); } }"},
            {"other/Child.java": "package other; public class Child extends lib.Api {}"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("other.Child#used()V", report)

    def test_removed_field_blocks(self):
        result, report = self.fixture("public class Api { public int value; }", "public class Api {}",
            {"app/Client.java": "package app; public class Client { int call() { return new lib.Api().value; } }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("lib.Api#valueI", report)

    def test_static_to_instance_blocks(self):
        result, report = self.fixture("public class Api { public static void used() {} }",
            "public class Api { public void used() {} }",
            {"app/Client.java": "package app; public class Client { void call() { lib.Api.used(); } }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("static/instance", report)

    def test_method_handle_reference_blocks(self):
        result, report = self.fixture("public class Api { public static void used() {} }", "public class Api {}",
            {"app/Client.java": "package app; public class Client { Runnable call() { return lib.Api::used; } }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("lib.Api#used()V", report)

    def test_removed_constructor_blocks(self):
        result, report = self.fixture("public class Api { public Api() {} }", "public class Api { public Api(int x) {} }",
            {"app/Client.java": "package app; public class Client { Object call() { return new lib.Api(); } }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("lib.Api#<init>()V", report)

    def test_precompiled_factory_cannot_instantiate_class_that_became_abstract(self):
        result, report = self.fixture(
            "public class Api { public Api() {} }",
            "public abstract class Api { public Api() {} }",
            {"app/Client.java": "package app; public class Client { "
                                "public static void main(String[] args) { other.Factory.create(); } }"},
            extra_old={"other/Factory.java": "package other; public class Factory { "
                                             "public static Object create() { return new lib.Api(); } }"},
        )
        runtime_classpath = [self.root / name for name in ("consumer.jar", "old-extra.jar", "new.jar")]
        runtime = subprocess.run(
            ["java", "-cp", os.pathsep.join(map(str, runtime_classpath)), "app.Client"],
            capture_output=True, text=True,
        )
        self.assertNotEqual(0, runtime.returncode)
        self.assertIn("InstantiationError: lib.Api", runtime.stderr)
        self.assertEqual(1, result.returncode, report + result.stderr)
        self.assertIn("unchanged-dependency!other.Factory\tlib.Api\tinstantiated class became abstract", report)

    def test_constructor_method_handle_cannot_instantiate_class_that_became_abstract(self):
        result, report = self.fixture(
            "public class Api {}",
            "public abstract class Api {}",
            {"app/Client.java": "package app; public class Client { "
                                "java.util.function.Supplier<lib.Api> factory() { return lib.Api::new; } }"},
        )
        self.assertEqual(1, result.returncode, report + result.stderr)
        self.assertIn("app.Client\tlib.Api\tinstantiated class became abstract", report)

    def test_abstract_superclass_and_array_element_type_remain_usable(self):
        result, report = self.fixture(
            "public class Api {}",
            "public abstract class Api {}",
            {"app/Client.java": "package app; public class Client extends lib.Api { "
                                "lib.Api[] types() { return new lib.Api[1]; } }"},
        )
        self.assertEqual(0, result.returncode, report + result.stderr)

    def test_reduced_access_blocks(self):
        result, report = self.fixture("public class Api { public void used() {} }",
            "public class Api { private void used() {} }",
            {"app/Client.java": "package app; public class Client { void call() { new lib.Api().used(); } }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("no longer accessible", report)

    def test_added_abstract_method_blocks_concrete_consumer(self):
        result, report = self.fixture("public interface Api {}",
            "public interface Api { void newRequirement(); }",
            {"app/Client.java": "package app; public class Client implements lib.Api {}"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("lacks new abstract implementation", report)

    def test_private_method_does_not_satisfy_new_interface_requirement(self):
        result, report = self.fixture("public interface Api {}",
            "public interface Api { void required(); }",
            {"app/Client.java": "package app; public class Client implements lib.Api { private void required() {} }"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("lacks new abstract implementation", report)

    def test_existing_public_implementation_satisfies_new_requirement(self):
        result, report = self.fixture("public interface Api {}",
            "public interface Api { void required(); }",
            {"app/Client.java": "package app; public class Client implements lib.Api { public void required() {} }"})
        self.assertEqual(0, result.returncode, report)

    def test_new_final_superclass_blocks(self):
        result, report = self.fixture("public class Api {}", "public final class Api {}",
            {"app/Client.java": "package app; public class Client extends lib.Api {}"})
        self.assertEqual(1, result.returncode, report)
        self.assertIn("superclass became final", report)

    def test_deleted_class_only_in_descriptor_blocks(self):
        old = self.compile("old", {"lib/Gone.java": "package lib; public class Gone {}"})
        consumer = self.compile("consumer", {"app/Client.java": "package app; public class Client { lib.Gone field; }"}, [old])
        empty = self.root / "empty.jar"
        with zipfile.ZipFile(empty, "w"):
            pass
        manifest = self.root / "inputs.tsv"
        manifest.write_text(f"old\tremoved-module\t{old}\nnew\tempty\t{empty}\nchanged\tremoved-module\t{old}\nproject\t:feature\t{consumer}\n")
        result = subprocess.run(["java", "-cp", os.pathsep.join([self.compiled.name, str(JAPICMP)]),
                                 "CheckDependencyLinkage", str(manifest), str(self.root / "risks.tsv")], capture_output=True, text=True)
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("lib.Gone", result.stdout)

    def test_scoped_dependency_copy_cannot_mask_removed_runtime_class(self):
        old = self.compile("old", {"lib/Gone.java": "package lib; public class Gone {}"})
        consumer = self.compile("consumer", {"app/Client.java": "package app; public class Client { lib.Gone field; }"}, [old])
        manifest = self.root / "inputs.tsv"
        manifest.write_text(f"old\tremoved-module\t{old}\nchanged\tremoved-module\t{old}\n"
                            f"consumer\tother-scope-copy\t{old}\nproject\t:app\t{consumer}\n")
        result = subprocess.run(["java", "-cp", os.pathsep.join([self.compiled.name, str(JAPICMP)]),
                                 "CheckDependencyLinkage", str(manifest), str(self.root / "risks.tsv")], capture_output=True, text=True)
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("lib.Gone", result.stdout)

    def test_checker_dependencies_cannot_supply_removed_runtime_class(self):
        old = self.compile("old", {"javassist/CtClass.java": "package javassist; public class CtClass {}"})
        consumer = self.compile("consumer", {"app/Client.java": "package app; public class Client { javassist.CtClass field; }"}, [old])
        manifest = self.root / "inputs.tsv"
        manifest.write_text(f"old\tremoved-module\t{old}\nchanged\tremoved-module\t{old}\nproject\t:app\t{consumer}\n")
        result = subprocess.run(["java", "-cp", os.pathsep.join([self.compiled.name, str(JAPICMP)]),
                                 "CheckDependencyLinkage", str(manifest), str(self.root / "risks.tsv")], capture_output=True, text=True)
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("javassist.CtClass", result.stdout)

    def introduced_reference(self, preexisting=False, provide_required=False):
        support = self.compile("support", {"support/Api.java": "package support; public class Api {}"})
        future = self.compile("future", {"support/Api.java": "package support; public class Api { public static void required() {} }"})
        call = "support.Api.required();"
        before = self.compile("before", {"lib/Updated.java": "package lib; public class Updated { public static void call() {"
            + (call if preexisting else "") + "} }"}, [future])
        after = self.compile("after", {"lib/Updated.java": "package lib; public class Updated { public static void call() {"
            + call + "} }"}, [future])
        consumer = self.compile("consumer", {"app/Client.java": "package app; public class Client { void call() { lib.Updated.call(); } }"}, [before])
        manifest = self.root / "inputs.tsv"
        manifest.write_text(f"old\tsupport:1\t{support}\nold\tupdated:1\t{before}\n"
            f"new\tsupport:1\t{future if provide_required else support}\nnew\tupdated:2\t{after}\n"
            f"changed\tupdated:1\t{before}\nchanged\tupdated:2\t{after}\nproject\t:app\t{consumer}\n")
        result = subprocess.run(["java", "-cp", os.pathsep.join([self.compiled.name, str(JAPICMP)]),
                                 "CheckDependencyLinkage", str(manifest), str(self.root / "risks.tsv")], capture_output=True, text=True)
        return result

    def test_updated_library_requires_missing_method_in_unchanged_dependency(self):
        result = self.introduced_reference()
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("support.Api#required()V", result.stdout)
        self.assertIn("introduces an unresolved reference", result.stdout)

    def test_existing_unresolved_dependency_reference_is_not_attributed_to_update(self):
        result = self.introduced_reference(preexisting=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("unresolved-in-both-graphs", (self.root / "referenced-apis.tsv").read_text())

    def test_new_dependency_requirement_passes_when_runtime_provides_it(self):
        result = self.introduced_reference(provide_required=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_missing_consumer_input_fails_closed(self):
        manifest = self.root / "inputs.tsv"
        manifest.write_text(f"project\t:app\t{self.root / 'missing.jar'}\n")
        result = subprocess.run(["java", "-cp", os.pathsep.join([self.compiled.name, str(JAPICMP)]),
                                 "CheckDependencyLinkage", str(manifest), str(self.root / "risks.tsv")], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)

    def test_empty_project_output_does_not_pass_with_dependency_classes(self):
        library = self.compile("library", {"lib/Api.java": "package lib; public class Api {}"})
        empty = self.root / "empty"
        empty.mkdir()
        manifest = self.root / "inputs.tsv"
        manifest.write_text(f"new\tlib:api:2\t{library}\nproject\t:app\t{empty}\n")
        result = subprocess.run(["java", "-cp", os.pathsep.join([self.compiled.name, str(JAPICMP)]),
                                 "CheckDependencyLinkage", str(manifest), str(self.root / "risks.tsv")], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn("Missing compiled project consumers", result.stderr)


if __name__ == "__main__":
    unittest.main()
