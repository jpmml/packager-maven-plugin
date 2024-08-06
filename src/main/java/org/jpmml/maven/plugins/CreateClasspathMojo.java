/*
 * Copyright (c) 2024 Villu Ruusmann
 */
package org.jpmml.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

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

			for(Artifact artifact : artifacts){
				elements.add(copyArtifactFile(artifact));
			}

			// XXX
			String classpathFileName = "classpath.txt";

			Path outputPath = (this.outputDirectory.toPath()).resolve(classpathFileName);

			try(Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)){

				for(Iterator<String> it = elements.iterator(); it.hasNext(); ){
					writer.write(it.next());

					if(it.hasNext()){
						writer.write('\n');
					}
				}
			}
		} catch(Exception e){
			throw new MojoExecutionException("Failed to create classpath", e);
		}
	}

	private String copyArtifactFile(Artifact artifact) throws IOException {
		File file = artifact.getFile();

		// XXX
		String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";

		Path inputPath = file.toPath();
		Path outputPath = (this.outputDirectory.toPath()).resolve(artifactFileName);

		Files.copy(inputPath, outputPath);

		return artifactFileName;
	}
}