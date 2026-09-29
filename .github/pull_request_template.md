## Summary

<!-- What changes and why, in a few sentences. Link the issue it closes. -->

## Test plan

<!-- The tests that cover it, and any manual run against the dev instance. -->

## Checklist

- [ ] One concern; split if it approaches a thousand changed lines
- [ ] Tests for every non-trivial branch; fixtures from the dev instance where the server is involved
- [ ] `detekt ktlintCheck lint alohaArchitectureCheck` green, no baseline grown
- [ ] Screenshots re-recorded only where the UI change is intended
- [ ] Accessibility: labels, 48 dp targets, headings and `paneTitle` on new screens, 200 % font preview
- [ ] Strings in `strings.xml` with translator comments
- [ ] No new exported component, permission or dependency without a line here explaining it
- [ ] `docs/` updated where behaviour changed
- [ ] AI tools were used for this contribution (commits carry `Assisted-by:`)
