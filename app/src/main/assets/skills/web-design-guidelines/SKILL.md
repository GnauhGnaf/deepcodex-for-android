# Web Design Guidelines

Comprehensive web design best practices for creating beautiful, usable web interfaces.

## Design principles

- **Clarity**: Users should immediately understand what the page does
- **Consistency**: Same patterns, colors, spacing throughout
- **Hierarchy**: Important elements are visually prominent
- **Responsive**: Works on mobile, tablet, and desktop
- **Accessible**: WCAG 2.1 AA compliant

## Color system

- Primary: 1-2 brand colors for key actions and accents
- Neutral: Grays for text, backgrounds, borders
- Semantic: Green (success), Red (error), Yellow (warning)
- 60-30-10 rule: 60% neutral, 30% primary, 10% accent

## Typography

- Sans-serif for UI (Inter, system-ui, -apple-system)
- Monospace for code (JetBrains Mono, Cascadia Code, monospace)
- Line height: 1.5 for body, 1.2 for headings
- Max line width: 65ch for readability

## Spacing

- Use 4px base unit (4, 8, 12, 16, 24, 32, 48, 64)
- Consistent padding within component groups
- Generous white space — don't crowd elements

## Layout patterns

- Card-based layouts for collections
- Sidebar + main content for apps
- Centered single column for landing pages
- Grid (12-column) for complex layouts

## Dark mode

- Never pure black (#000) — use dark grays (#111, #1a1a2e)
- Reduce contrast slightly vs light mode
- Test all states in both modes
