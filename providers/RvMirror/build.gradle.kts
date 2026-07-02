import com.flixclusive.model.provider.Language
import com.flixclusive.model.provider.ProviderType
import com.flixclusive.model.provider.Status

flxProvider {
    description =
        """
        Netflix, Prime Video and Disney+ Hotstar mirror. Browse each platform as its own
        catalog, search across the mirror, and stream movies & shows. Discovered mirror
        ids are saved locally so catalogs grow as the provider sees more titles.
        """.trimIndent()

    changelog =
        """
        # RvMirror 1.1.0
        - Persist discovered mirror ids from home, search, metadata suggestions, and episodes
        - Merge saved ids back into catalogs to show more titles over time
        - Keep movie/show metadata and HLS stream extraction wired to the saved ids
        """.trimIndent()

    versionMajor = 1
    versionMinor = 1
    versionPatch = 0
    versionBuild = 0

    iconUrl = "https://raw.githubusercontent.com/ravikantkrsingh108-dot/RvMirror/main/RvMirror/icon.png"

    language = Language.Multiple
    providerType = ProviderType.All
    status = Status.Working
}