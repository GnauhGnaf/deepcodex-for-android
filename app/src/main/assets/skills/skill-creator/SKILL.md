# Skill Creator

Create and manage Codex skills — reusable guides that teach the AI how to handle specific tasks, tools, or domains.

## What is a skill?

A skill is a Markdown file (`.md`) stored in `.codex/skills/`. It contains instructions, examples, and best practices for a specific task. When you read a skill, you gain specialized knowledge for that task.

## Skill file format

```markdown
# Skill Title

Brief description of what this skill helps with.

## When to use

Describe when this skill should be consulted.

## Instructions

Step-by-step guidance...
```

## Creating a new skill

1. Identify a task that would benefit from reusable instructions
2. Write a concise Markdown file with clear guidance
3. Save it: `write_file` with path `.codex/skills/<name>.md`
4. The skill is immediately available for future use

## Installing skills from skills.sh

The [skills.sh](https://skills.sh) registry provides community skills. To install:

1. Use `run_command` with curl/wget to fetch the raw SKILL.md from GitHub:
   ```sh
   curl -sL "https://raw.githubusercontent.com/vercel-labs/agent-skills/main/skills/<skill-name>/SKILL.md" -o /tmp/skill.md
   ```
2. Read the downloaded file with `read_file /tmp/skill.md`
3. Save it to `.codex/skills/<skill-name>.md` with `write_file`
4. Alternative: use the `skills.sh` helper script (run `sh skills.sh` for usage)

## Popular skills on skills.sh

| Skill | Description |
|-------|-------------|
| `vercel-react-best-practices` | React and Next.js best practices |
| `web-design-guidelines` | Web design guidelines and patterns |
| `frontend-design` | Frontend design patterns and UX |
| `supabase-best-practices` | Supabase database and auth |
| `vue-best-practices` | Vue.js best practices |
| `three-js-best-practices` | Three.js 3D graphics |
| `python-best-practices` | Python development patterns |
| `testing-best-practices` | Testing strategies and patterns |

## Best practices

- Keep skills focused on ONE topic
- Use code examples for clarity
- Include "gotchas" and common pitfalls
- Test the skill by using it on a real task
- Update skills when you discover better approaches
