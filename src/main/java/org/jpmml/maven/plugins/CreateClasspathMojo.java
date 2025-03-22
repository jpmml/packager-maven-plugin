/*
 * Copyright (c) 2024 Villu Ruusmann
 */
package org.jpmml.maven.plugins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo (
	name = "create-classpath",
	defaultPhase = LifecyclePhase.PACKAGE,
	requiresDependencyResolution = ResolutionScope.RUNTIME
)
public class CreateClasspathMojo extends AbstractMojo {

	@Parameter (
		defaultValue = "${project}",
		required = true,
		readonly = true
	)
	MavenProject project;

	@Parameter
	Minify minify;

	@Parameter (
		required = true
	)
	File outputDirectory;


	@Override
	public void execute() throws MojoExecutionException {
		List<Artifact> artifacts = new ArrayList<>();

		Artifact projectArtifact = this.project.getArtifact();

		if(Objects.equals(projectArtifact.getType(), "jar")){
			artifacts.add(projectArtifact);
		}

		List<Artifact> dependencyArtifacts = new ArrayList<>(this.project.getArtifacts());

		Comparator<Artifact> comparator = new Comparator<Artifact>(){

			@Override
			public int compare(Artifact left, Artifact right){
				return (left.getArtifactId()).compareTo(right.getArtifactId());
			}
		};

		Collections.sort(dependencyArtifacts, comparator);

		for(Artifact dependencyArtifact : dependencyArtifacts){

			if(Objects.equals(dependencyArtifact.getType(), "jar")){
				artifacts.add(dependencyArtifact);
			}
		}

		try {
			if(!this.outputDirectory.exists()){
				this.outputDirectory.mkdirs();
			}

			List<String> elements = new ArrayList<>();

			if(this.minify != null){
				Predicate<JarEntry> minifyPredicate = this.minify.createMinifyPredicate(artifacts);

				for(Artifact artifact : artifacts){

					if(this.minify.accept(artifact)){
						elements.add(copyArtifactFile(artifact, minifyPredicate));
					} else

					{
						elements.add(copyArtifactFile(artifact));
					}
				}
			} else

			{
				for(Artifact artifact : artifacts){
					elements.add(copyArtifactFile(artifact));
				}
			}

			File outputFile = new File(this.outputDirectory, "classpath.txt");

			try(OutputStream os = new FileOutputStream(outputFile)){
				Writer writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));

				for(Iterator<String> it = elements.iterator(); it.hasNext(); ){
					writer.write(it.next());

					if(it.hasNext()){
						writer.write('\n');
					}
				}

				writer.close();
			}
		} catch(Exception e){
			throw new MojoExecutionException("Failed to create classpath", e);
		}
	}

	private String copyArtifactFile(Artifact artifact, Predicate<JarEntry> predicate) throws IOException {
		String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + "-repackaged.jar";

		File inputFile = artifact.getFile();
		File outputFile = new File(this.outputDirectory, artifactFileName);

		try(JarFile jarFile = new JarFile(inputFile)){

			try(JarOutputStream jarOs = new JarOutputStream(new FileOutputStream(outputFile))){
				byte[] buffer = new byte[16 * 1024];

				for(Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ){
					JarEntry jarEntry = entries.nextElement();

					if(predicate.test(jarEntry)){
						JarEntry safeJarEntry = new JarEntry(jarEntry);

						int method = safeJarEntry.getMethod();
						switch(method){
							case ZipEntry.STORED:
								break;
							case ZipEntry.DEFLATED:
								safeJarEntry.setCompressedSize(-1L);
								break;
							default:
								throw new IllegalArgumentException();
						}

						jarOs.putNextEntry(safeJarEntry);

						try(InputStream jarIs = jarFile.getInputStream(jarEntry)){

							while(true){
								int length = jarIs.read(buffer);
								if(length < 0){
									break;
								}

								jarOs.write(buffer, 0, length);
							}
						}

						jarOs.closeEntry();
					}
				}
			}
		}

		return artifactFileName;
	}

	private String copyArtifactFile(Artifact artifact) throws IOException {
		String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";

		File inputFile = artifact.getFile();
		File outputFile = new File(this.outputDirectory, artifactFileName);

		Files.copy(inputFile.toPath(), outputFile.toPath());

		return artifactFileName;
	}
}