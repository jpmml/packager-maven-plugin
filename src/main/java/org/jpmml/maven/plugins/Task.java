/*
 * Copyright (c) 2025 Villu Ruusmann
 */
package org.jpmml.maven.plugins;

import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Parameter;

abstract
public class Task {

	@Parameter (
		required = true
	)
	Set<String> artifacts;


	public Task(){
	}

	public boolean accept(Artifact artifact){

		if(this.artifacts.contains(Task.ID_ANY + ":" + Task.ID_ANY)){
			return true;
		} else

		if(this.artifacts.contains(artifact.getGroupId() + ":" + Task.ID_ANY)){
			return true;
		} else

		if(this.artifacts.contains(artifact.getGroupId() + ":" + artifact.getArtifactId())){
			return true;
		}

		return false;
	}

	private static final String ID_ANY = "*";
}