/*
 * Copyright (c) 2025 Villu Ruusmann
 */
package org.jpmml.maven.plugins;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javassist.ClassPool;
import javassist.CtClass;
import org.apache.maven.plugins.annotations.Parameter;

public class Modify extends Task {

	@Parameter
	List<ClassTransformer> transformers = Collections.emptyList();

	private Function<byte[], byte[]> modifyFunction = null;


	public Function<byte[], byte[]> getModifyFunction(){
		return this.modifyFunction;
	}

	void createModifyFunction(){
		Function<byte[], byte[]> function = new Function<byte[], byte[]>(){

			@Override
			public byte[] apply(byte[] bytes){
				ClassPool classPool = ClassPool.getDefault();

				byte[] result;

				try {
					CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(bytes), false);

					ctClass = transform(ctClass);

					result = ctClass.toBytecode();

					ctClass.detach();
				} catch(Exception e){
					throw new RuntimeException(e);
				}

				return result;
			}
		};

		this.modifyFunction = function;
	}

	private CtClass transform(CtClass ctClass){

		if(!this.transformers.isEmpty()){

			for(ClassTransformer transformer : this.transformers){
				ctClass = transformer.apply(ctClass);
			}

			ctClass.rebuildClassFile();
		}

		return ctClass;
	}
}