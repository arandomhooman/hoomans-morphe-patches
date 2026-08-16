package app.morphe.extension.twitch.emotes;

final class Emote {
    final String name;
    final String url;
    final boolean animated;

    Emote(String name, String url, boolean animated) {
        this.name = name;
        this.url = url;
        this.animated = animated;
    }
}
