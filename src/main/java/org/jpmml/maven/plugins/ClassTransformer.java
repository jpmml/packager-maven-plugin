/*
 * Copyright (c) 2025 Villu Ruusmann
 */
package org.jpmml.maven.plugins;

import java.util.function.Function;

import javassist.CtClass;

interface ClassTransformer extends Function<CtClass, CtClass> {
}