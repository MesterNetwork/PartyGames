package info.mester.network.partygames

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

@Suppress("UnstableApiUsage", "unused")
class Loader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()
        resolver.addRepository(
            RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build(),
        )
        resolver.addRepository(
            RemoteRepository
                .Builder("rapture", "default", "https://repo.rapture.pw/repository/maven-releases/")
                .build(),
        )
        resolver.addDependency(Dependency(DefaultArtifact("com.squareup.okhttp3:okhttp:4.12.0"), null))
        classpathBuilder.addLibrary(resolver)
    }
}
