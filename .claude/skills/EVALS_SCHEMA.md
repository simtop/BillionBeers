# Skill format check fixtures

`scripts/test_check_skill_format.py` is a deterministic fixture-based test for the skill
frontmatter checker. It makes no model calls and does not grade skill usefulness.

The suite lives in `scripts/fixtures/skill-format/cases.json` and has this shape:

```json
{
  "cases": [
    {
      "id": "folded-description",
      "fixture": "folded-description.fixture",
      "expected_name": "sample-skill",
      "expected_errors": []
    }
  ]
}
```

- `id` is a unique lowercase kebab-case case name.
- `fixture` is a `.fixture` file in the same directory containing a complete `SKILL.md`.
- `expected_name` is the skill directory name the fixture is checked against.
- `expected_errors` is the exact ordered list returned by `validate_skill_text`.

Keep fixtures small and synthetic. A passing case should cover a supported frontmatter form;
failing cases should cover one malformed condition each. Repository checks read only
Git-tracked `.claude/skills/*/SKILL.md` files so local synced skills cannot make CI depend on
developer state.
