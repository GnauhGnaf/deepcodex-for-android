#!/bin/sh
# Codex Skills Manager — browse and install skills from skills.sh registry
# Usage: sh /skills/skills.sh [list|search <kw>|install <name>]

SKILLS_DIR="/skills"
CATALOG_URL="https://raw.githubusercontent.com/vercel-labs/agent-skills/main/skills"

# Built-in catalog of popular skills (name|description)
SKILLS_CATALOG="
skill-creator|Create and manage Codex skills
vercel-react-best-practices|React and Next.js best practices from Vercel
web-design-guidelines|Web design patterns and UI guidelines
frontend-design|Frontend design patterns and UX best practices
supabase-best-practices|Supabase database, auth, and edge functions
vue-best-practices|Vue.js composition API and patterns
three-js-best-practices|Three.js 3D graphics and WebGL
testing-best-practices|Unit, integration, and e2e testing strategies
python-best-practices|Python development patterns and tooling
node-best-practices|Node.js backend development patterns
system-prompt-writer|Create effective system prompts for AI agents
cli-tool-builder|Build CLI tools with Node.js
"

download_skill() {
    NAME="$1"
    URL="$CATALOG_URL/$NAME/SKILL.md"
    DEST="$SKILLS_DIR/${NAME}.md"

    echo "Downloading $NAME from skills.sh..."
    if command -v curl >/dev/null 2>&1; then
        if curl -fsSL "$URL" -o "$DEST"; then
            echo "Installed: $DEST"
        else
            echo "ERROR: Failed to download $NAME (not found or network error)"
            return 1
        fi
    elif command -v wget >/dev/null 2>&1; then
        if wget -q "$URL" -O "$DEST"; then
            echo "Installed: $DEST"
        else
            echo "ERROR: Failed to download $NAME"
            return 1
        fi
    else
        echo "ERROR: Neither curl nor wget available. Install them first:"
        echo "  apk add curl"
        return 1
    fi
}

list_skills() {
    echo "Available skills (from skills.sh):"
    echo "================================"
    echo "$SKILLS_CATALOG" | while IFS="|" read -r name desc; do
        [ -z "$name" ] && continue
        name=$(echo "$name" | tr -d ' ')
        desc=$(echo "$desc" | tr -d ' ')
        installed=""
        [ -f "$SKILLS_DIR/${name}.md" ] && installed=" [installed]"
        printf "  %-35s %s%s\n" "$name" "$desc" "$installed"
    done
    echo ""
    echo "Install a skill: sh /skills/skills.sh install <name>"
}

search_skills() {
    KW="$1"
    echo "Searching for: $KW"
    echo "===================="
    FOUND=0
    echo "$SKILLS_CATALOG" | while IFS="|" read -r name desc; do
        [ -z "$name" ] && continue
        if echo "$name $desc" | grep -iq "$KW"; then
            name=$(echo "$name" | tr -d ' ')
            desc=$(echo "$desc" | tr -d ' ')
            printf "  %-35s %s\n" "$name" "$desc"
            FOUND=1
        fi
    done
}

case "${1:-list}" in
    list)
        list_skills
        ;;
    search)
        search_skills "${2:-}"
        ;;
    install)
        if [ -z "${2:-}" ]; then
            echo "Usage: sh /skills/skills.sh install <skill-name>"
            echo "Run 'sh /skills/skills.sh list' to see available skills."
            exit 1
        fi
        download_skill "$2"
        ;;
    *)
        echo "Codex Skills Manager"
        echo "Usage: sh /skills/skills.sh [list|search <kw>|install <name>]"
        echo ""
        echo "  list              List all available skills"
        echo "  search <keyword>  Search skills by keyword"
        echo "  install <name>    Install a skill from skills.sh"
        ;;
esac
