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
import java.util.function.Function;
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
	int compressionLevel = -1;

	@Parameter
	Minify minify;

	@Parameter
	Modify modify;

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

			if(this.minify != null || this.modify != null){

				if(this.minify != null){
					this.minify.createMinifyPredicate(artifacts);
				} // End if

				if(this.modify != null){
					this.modify.createModifyFunction();
				}

				for(Artifact artifact : artifacts){
					elements.add(copyArtifactFile(artifact, this.minify, this.modify));
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

	private String copyArtifactFile(Artifact artifact, Minify minify, Modify modify) throws IOException {
		String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + "-repackaged.jar";

		File inputFile = artifact.getFile();
		File outputFile = new File(this.outputDirectory, artifactFileName);

		try(JarFile jarFile = new JarFile(inputFile)){
			Predicate<JarEntry> minifyPredicate = null;
			if(minify != null && minify.accept(artifact)){
				minifyPredicate = minify.getMinifyPredicate();
			}

			Function<byte[], byte[]> modifyFunction = null;
			if(modify != null && modify.accept(artifact)){
				modifyFunction = modify.getModifyFunction();
			}

			try(JarOutputStream jarOs = new JarOutputStream(new FileOutputStream(outputFile))){

				if(this.compressionLevel != -1){
					jarOs.setLevel(this.compressionLevel);
				}

				entries:
				for(Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ){
					JarEntry jarEntry = entries.nextElement();

					minify:
					if(minifyPredicate != null){

						if(minifyPredicate.test(jarEntry)){
							break minify;
						}

						continue entries;
					}

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

					modify:
					if(modifyFunction != null){
						String name = jarEntry.getName();

						if(!name.endsWith(".class")){
							break modify;
						}

						byte[] bytes;

						try(InputStream jarIs = jarFile.getInputStream(jarEntry)){
							bytes = jarIs.readAllBytes();
						}

						bytes = modifyFunction.apply(bytes);

						safeJarEntry.setSize(bytes.length);

						jarOs.write(bytes, 0, bytes.length);

						continue entries;
					}

					try(InputStream jarIs = jarFile.getInputStream(jarEntry)){
						jarIs.transferTo(jarOs);
					}

					jarOs.closeEntry();
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