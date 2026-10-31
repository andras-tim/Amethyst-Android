# Amethyst::Dras - agent instructions

## Stay clean against upstream

This repo is a fork of [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android). Validate every
change against the current upstream, not against the last known base:

```bash
git fetch upstream
git rev-list --count HEAD..upstream/v3_openjdk                   # how far behind
git merge-tree --write-tree --name-only HEAD upstream/v3_openjdk # trial merge, lists the conflicts
git diff --stat "$(git merge-base upstream/v3_openjdk HEAD)" HEAD
```

- Keep changes in upstream files small and new code in new files, so the next merge stays easy.
- Don't change upstream code without a need. No site or pack specific value in the APK.
- Report the result with the change: commits behind, conflicting files, and what the change added to them.
