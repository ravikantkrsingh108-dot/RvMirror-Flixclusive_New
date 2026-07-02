import com.flixclusive.model.provider.Language
import com.flixclusive.model.provider.ProviderType
import com.flixclusive.model.provider.Status

flxProvider {
    providerName = "RvMirror"

    description =
        """
        Netflix, Prime Video and Disney+ Hotstar mirror. Browse each platform as its own
        catalog, search across the mirror, and stream movies & shows. Discovered mirror
        ids are saved locally so catalogs grow as the provider sees more titles.
        """.trimIndent()

    changelog =
        """
        # RvMirror 1.2.0
        - Rebuild provider package from the Flixclusive template layout
        - Move the entry class to com.flixclusive.provider.rvmirror.RvMirror
        - Include a compatibility entry class for older cached metadata

        # RvMirror 1.1.1
        - Keep the provider entry class packaged so Flixclusive can load it
        - Align Android namespace with the provider class package
        
        # RvMirror 1.1.0
        - Persist discovered mirror ids from home, search, metadata suggestions, and episodes
        - Merge saved ids back into catalogs to show more titles over time
        - Keep movie/show metadata and HLS stream extraction wired to the saved ids
        """.trimIndent()

    versionMajor = 1
    versionMinor = 2
    versionPatch = 0
    versionBuild = 0

    iconUrl = "https://raw.githubusercontent.com/ravikantkrsingh108-dot/RvMirror/main/RvMirror/icon.png"

    language = Language.Multiple
    providerType = ProviderType.All
    status = Status.Working
}