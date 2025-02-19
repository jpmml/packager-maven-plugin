/*
 * Copyright (c) 2024 Villu Ruusmann
 */
package org.jpmml.maven.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Parameter;

public class Minify {

	@Parameter (
		required = true
	)
	Set<String> artifacts;

	@Parameter
	Set<String> entryPoints = Collections.emptySet();

	@Parameter
	Set<String> propertyEntryPoints = Collections.emptySet();

	@Parameter
	Set<String> serviceEntryPoints = Collections.emptySet();


	public boolean accept(Artifact artifact){
		return this.artifacts.contains(artifact.getGroupId() + ":" + artifact.getArtifactId());
	}

	Set<String> getEntryPoints(){
		return this.entryPoints;
	}

	Set<String> getEntryPoints(File file) throws IOException {
		Set<String> result = new LinkedHashSet<>();

		try(JarFile jarFile = new JarFile(file)){

			for(String propertyEntryPoint : this.propertyEntryPoints){
				JarEntry jarEntry = (JarEntry)jarFile.getEntry(propertyEntryPoint);

				if(jarEntry == null){
					continue;
				}

				result.addAll(loadPropertyValues(jarFile, jarEntry));
			} // End for

			for(String serviceEntryPoint : this.serviceEntryPoints){
				JarEntry jarEntry = (JarEntry)jarFile.getEntry(serviceEntryPoint);

				if(jarEntry == null){
					continue;
				}

				result.addAll(loadServices(jarFile, jarEntry));
			}
		}

		return result;
	}

	static
	private Set<String> loadPropertyValues(JarFile jarFile, JarEntry jarEntry) throws IOException {
		Properties properties = new Properties();

		try(InputStream is = jarFile.getInputStream(jarEntry)){
			properties.load(is);
		}

		return (properties.values()).stream()
			.map(String.class::cast)
			.collect(Collectors.toSet());
	}

	static
	private Set<String> loadServices(JarFile jarFile, JarEntry jarEntry) throws IOException {
		Set<String> result = new LinkedHashSet<>();

		try(InputStream is = jarFile.getInputStream(jarEntry)){
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

			while(true){
				String line = reader.readLine();

				if(line == null){
					break;
				}

				line = line.trim();

				int hash = line.indexOf('#');
				if(hash > -1){
					line = line.substring(0, hash);
				} // End if

				if(!line.isEmpty()){
					result.add(line);
				}
			}

			reader.close();
		}

		return result;
	}
}