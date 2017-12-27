package io.jenkins.blueocean.maven.plugin;

import net.sf.json.JSONObject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.jenkinsci.maven.plugins.hpi.AbstractJenkinsMojo;
import org.jenkinsci.maven.plugins.hpi.MavenArtifact;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Goal which copies upstream Blue Ocean Javascript in an npm-compatible
 * structure locally
 */
@Mojo(name = "process-node-dependencies", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ProcessUpstreamDependenciesMojo extends AbstractJenkinsMojo {
    /**
     * Location of the file.
     */
    @Parameter(defaultValue = "${project.basedir}", property = "baseDir", required = true)
    private File baseDir;

    /**
     * Location of the node_modules
     */
    @Parameter(defaultValue = "${project.basedir}/node_modules", property = "nodeModulesDir", required = false)
    private File nodeModulesDirectory;

    /**
     * Whether to log debugging info
     */
    @Parameter(defaultValue = "false", property = "debugLog", required = false)
    private boolean debugLog = false;

    @Component
    protected DependencyGraphBuilder graphBuilder;

    /**
     * Execute upstream lookups
     */
    @Override
    public void execute() throws MojoExecutionException {
        // Skip non-js projects
        if (!new File(baseDir, "package.json").canRead()) {
            getLog().info("Skipping blueocean dependency install for non-js project: " + project.getArtifactId());
            return;
        }

        int copiedFiles = 0;

        long start = System.currentTimeMillis(), last = start;
        List<PackageJsonMavenArtifact> artifacts = new ArrayList<>();
        try {
            DependencyNode dependencyGraph = graphBuilder.buildDependencyGraph(project, null);

            if (debugLog || getLog().isDebugEnabled()) getLog().info("buildDependencyGraph took: " + (System.currentTimeMillis() - last));
            last = System.currentTimeMillis();

            collectBlueoceanDependencies(dependencyGraph, artifacts, new HashSet<URI>());

            if (debugLog || getLog().isDebugEnabled()) getLog().info("collectBlueoceanDependencies took: " + (System.currentTimeMillis() - last));
            last = System.currentTimeMillis();

            if (artifacts.isEmpty()) {
                getLog().info("No upstream blueocean dependencies found for: " + project.getArtifactId());
                return;
            }

            File nodeModulesOutputDir = nodeModulesDirectory;

            if (!nodeModulesOutputDir.exists()) {
                if (!nodeModulesOutputDir.mkdirs()) {
                    throw new MojoExecutionException("Unable to make node_modules directory: " + nodeModulesOutputDir.getCanonicalPath());
                }
            }

            getLog().info("Installing upstream dependencies...");

            for (PackageJsonMavenArtifact jsonArtifact : artifacts) {
                MavenArtifact artifact = jsonArtifact.getMavenArtifact();
                JSONObject packageJson = jsonArtifact.getPackageJson();

                String name = packageJson.getString("name");
                String[] subdirs = name.split("/");

                File outDir = nodeModulesDirectory;
                for (String subDir : subdirs) {
                    outDir = new File(outDir, subDir);
                }

                File artifactFile = artifact.getFile();
                long artifactLastModified = artifactFile.lastModified();

                if (!outDir.exists()) {
                    if (!outDir.mkdirs()) {
                        throw new MojoExecutionException("Unable to make module output directory: " + outDir.getCanonicalPath());
                    }
                }

                if (debugLog || getLog().isDebugEnabled()) getLog().info("Processing artifact with package.json: " + artifact.getFile().toURI());

                int read = 0;
                byte[] buf = new byte[1024*8];

                try (ZipInputStream jar = new ZipInputStream(new BufferedInputStream(new FileInputStream(artifact.getFile())))) {
                    ZipEntry entry;
                    while ((entry = jar.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            continue;
                        }
                        File outFile = new File(outDir, entry.getName());
                        if (!outFile.exists() || outFile.lastModified() < artifactLastModified) {
                            if (debugLog || getLog().isDebugEnabled()) getLog().info("Copying file: " + outFile.getAbsolutePath());
                            File parentFile = outFile.getParentFile();
                            if (!parentFile.exists()) {
                                if (!parentFile.mkdirs()) {
                                    throw new MojoExecutionException("Unable to make parent directory for: " + outFile.getCanonicalPath());
                                }
                            }

                            try (FileChannel fc = new RandomAccessFile(outFile, "rw").getChannel()) {
                                ByteBuffer out = fc.map(FileChannel.MapMode.READ_WRITE, 0, entry.getSize());
                                while ((read = jar.read(buf)) >= 0) {
                                    out.put(buf, 0, read);
                                }
                            }

                            copiedFiles++;
                        } else {
                            if (debugLog || getLog().isDebugEnabled()) getLog().info("Skipping file: " + outFile.getAbsolutePath() + " time difference: " + (outFile.lastModified() - artifactLastModified));
                        }
                    }
                }
            }
        } catch (DependencyGraphBuilderException | IOException e) {
            throw new RuntimeException(e);
        }

        if (debugLog || getLog().isDebugEnabled()) getLog().info("Done installing blueocean dependencies for " + project.getArtifactId() + " in " + (System.currentTimeMillis() - last) + "ms");

        getLog().info("Installed dependencies; " + copiedFiles + " files, took: " + (System.currentTimeMillis() - start) + "ms");
    }

    /**
     * Simple file as a byte array
     */
    protected interface Contents {
        String getFileName();
        byte[] getData() throws IOException;
    }

    protected interface PackageJsonMavenArtifact {
        JSONObject getPackageJson();
        MavenArtifact getMavenArtifact();
    }

    /**
     * Finds jar entries matching a specific path using the {@link FileSystem#getPathMatcher(String)} logic
     */
    private Contents getJarEntry(@Nonnull URI jarFile, @Nonnull final String fileName) throws IOException {
        try {
            final URI fileUri = new URI("jar", jarFile.toString(),  null);
            try (FileSystem fileSystem = FileSystems.newFileSystem(fileUri, Collections.EMPTY_MAP)) {
                final Path source = fileSystem.getPath(fileName);
                if (source != null) {
                    final byte[] contents = Files.readAllBytes(source); // this is what fails with NoSuchFileException
                    return new Contents() {
                        public String getFileName() {
                            return fileName;
                        }
                        public byte[] getData() throws IOException {
                            return contents;
                        }
                    };
                }
            } catch(NoSuchFileException e) {
                // no problem
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Collects all "blue ocean-like" upstream dependencies
     */
    private void collectBlueoceanDependencies(@Nonnull DependencyNode node, @Nonnull List<PackageJsonMavenArtifact> results, @Nonnull Set<URI> visited) throws IOException {
        final MavenArtifact artifact = wrap(node.getArtifact());
        boolean addChildren = node.getArtifact().equals(project.getArtifact()); // always add children of local project
        MavenArtifact packageJsonArtifact = null;
        if (!addChildren) { // not the local project
            URI jarUri = artifact.getFile().toURI();
            if (visited.contains(jarUri)) {
                return;
            }
            visited.add(jarUri);
            if (debugLog || getLog().isDebugEnabled()) getLog().info("Testing artifact for Blue Ocean plugins: " + artifact.toString());
            Contents jarEntry = getJarEntry(jarUri, "package.json");
            if (jarEntry != null) {
                getLog().info("Adding upstream Blue Ocean plugin: " + artifact.toString());
                addChildren = true;
                final JSONObject packageJson = JSONObject.fromObject(new String(jarEntry.getData(), "utf-8"));
                results.add(new PackageJsonMavenArtifact() {
                    @Override
                    public JSONObject getPackageJson() {
                        return packageJson;
                    }
                    @Override
                    public MavenArtifact getMavenArtifact() {
                        return artifact;
                    }
                });
            }
        }

        if (addChildren) { // only traverse up until we find a non-blue ocean project
            for (DependencyNode child : node.getChildren()) {
                collectBlueoceanDependencies(child, results, visited);
            }
        }
    }
}
