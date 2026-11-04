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
- Texts of the fork go into `values/strings_dras.xml`, their Hungarian into `values-hu/strings_dras.xml`. The
  `values*/strings.xml` files stay as upstream and its Crowdin have them; a Hungarian text Crowdin lacks also goes into
  `values-hu/strings_dras.xml`. When a merge brings it from Crowdin, the build fails with "Duplicate resources": drop
  ours.
- Report the result with the change: commits behind, conflicting files, and what the change added to them.

## The setup site describes this app

The setup site (`docs/` and `tools/site.py` of `andras-tim/mc-dras-modpack`) walks players through this app: its
screens and labels as a Hungarian phone shows them, the browser login icon, the profile editor fields and the APK
size. A change to any of them updates the site in the same round; the list of what the site repeats is in the infra
repo's `docker.mc-java` `DEVELOPMENT.md`.

## Releases

Release tags are `<upstream version>-dras.<n>` (e.g. `1.1.7-dras.7`): signed, annotated, with a bullet changelog in the
tag message, and a GitHub release taken from it. A release only when the user asks for it at the end of a round:
fixes collect on the branch, not one release per fix.
