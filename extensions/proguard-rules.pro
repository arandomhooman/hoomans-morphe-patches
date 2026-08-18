# Extension classes are merged into the host app's dex. The host is itself R8-obfuscated and its
# classes occupy bare root-package names (a, b, c, ...). If R8 renames an extension class to a bare
# name too, it collides with a host class of the same name, and after the merge the reference resolves
# to the host's class. That breaks class verification: e.g. EmoteImageLoader's LruCache subclass gets
# renamed to `o`, but the merged `o` is an unrelated host class, so ART rejects EmoteSupport with
# "Reference: o not instance of android.util.LruCache" and the whole feature (and chat) dies. (#196)
#
# Keep every extension class at its fully-qualified name so it can never collide with a host class.
# Member obfuscation would be safe (members are class-scoped), but the extension is tiny, so keep it
# whole for simplicity and readable merged output.
-keep class app.morphe.extension.** { *; }

# The keep above cannot cover R8-synthesised classes (lambda impls, API-desugaring outlines), which R8
# otherwise emits with bare names in the root package and which then collide with host classes the same
# way. Force every class R8 does rename into the extension package so none land in the root namespace.
-repackageclasses 'app.morphe.extension.twitch.emotes'
