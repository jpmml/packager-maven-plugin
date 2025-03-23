/*
 * Copyright (c) 2025 Villu Ruusmann
 */
package org.jpmml.maven.plugins;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;

public class RemoveDebugInformationTransformer implements ClassTransformer {

	@Override
	public CtClass apply(CtClass ctClass){
		CtMethod[] ctMethods = ctClass.getDeclaredMethods();

		for(CtMethod ctMethod : ctMethods){
			MethodInfo methodInfo = ctMethod.getMethodInfo();

			CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
			if(codeAttribute != null){
				removeAttribute(codeAttribute, LineNumberAttribute.tag);

				removeAttribute(codeAttribute, LocalVariableAttribute.tag);
				removeAttribute(codeAttribute, LocalVariableAttribute.typeTag);
			}
		}

		return ctClass;
	}

	static
	private void removeAttribute(CodeAttribute codeAttribute, String name){
		List<AttributeInfo> attributeInfos = codeAttribute.getAttributes();

		for(Iterator<AttributeInfo> it = attributeInfos.iterator(); it.hasNext(); ){
			AttributeInfo attributeInfo = it.next();

			if(Objects.equals(attributeInfo.getName(), name)){
				it.remove();
			}
		}
	}
}