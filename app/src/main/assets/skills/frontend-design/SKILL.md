# Frontend Design

Practical frontend design patterns for creating polished web UIs.

## Component patterns

### Button hierarchy
- Primary: filled, brand color — 1 per section max
- Secondary: outlined or ghost
- Tertiary: text-only for low-priority actions

### Card design
- Subtle border + shadow: `border: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.1);`
- Padding: 16-24px inside cards
- Border-radius: 8-12px

### Form design
- Label above input (not placeholder-as-label)
- Validation on blur, not just on submit
- Error messages specific and actionable
- Input height: 40-48px for touch targets

### Navigation
- Top nav for 2-5 sections
- Sidebar for 5+ sections
- Breadcrumbs for deep hierarchies
- Active state clearly indicated

## Loading states

- Skeleton screens (not spinners) for page loads
- Spinners for actions < 2 seconds
- Progress bars for multi-step processes
- Optimistic updates where possible

## Empty states

- Illustration + heading + description
- Clear CTA button
- Never show "No results" without guidance

## Micro-interactions

- Hover: subtle color shift or shadow increase
- Click: brief scale down (0.97x)
- Transitions: 150-300ms ease-out
- Focus rings: 2px solid outline for keyboard nav

## Mobile-first

- Design at 375px first, scale up
- Touch targets minimum 44x44px
- No hover-only interactions on critical paths
- Bottom sheet > modal on mobile
