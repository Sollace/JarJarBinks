package com.blazeloader.util.transformers;

import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.blazeloader.bl.obf.AccessLevel;
import com.blazeloader.bl.obf.BLOBF;
import com.blazeloader.bl.obf.OBFLevel;
import com.blazeloader.util.transformers.mapping.DefaultTransformationMap;
import com.blazeloader.util.transformers.mapping.TransformationMap;
import com.blazeloader.util.transformers.transformations.FieldSelector;
import com.blazeloader.util.transformers.transformations.FinalityTransformation;
import com.blazeloader.util.transformers.transformations.MethodSelector;
import com.blazeloader.util.transformers.transformations.PublicityTransformation;
import com.blazeloader.util.transformers.transformations.TargetSelector;
import com.blazeloader.util.transformers.transformations.Transformation;

import net.acomputerdog.OBFUtil.map.TargetType;
import net.acomputerdog.OBFUtil.parse.types.ONFParser.DetectedTransformation;
import net.acomputerdog.core.java.Patterns;

public class ONFTransformer {
	private static boolean initialised = false;
	private static List<DetectedTransformation> onfs = null;

	public static void setONFS(List<DetectedTransformation> transformations) {
		ONFTransformer.onfs = transformations;
	}
	
	/**
     * TransformationMap containing class-specific transformations
     */
    private final TransformationMap transformations = new DefaultTransformationMap();
    
	public ONFTransformer() {
		if (initialised) throw new IllegalStateException("Cannot create more than one ONFTransformer!");
        if (onfs == null) BLOBF.OBF.equals(null); //Force load BLOBF table
		initialised = true;
        for (DetectedTransformation i : onfs) loadTransformation(i);
        onfs = null;
	}
	
	private void loadTransformation(DetectedTransformation transform) {
		String name;
		String clName;
		if (transform.isGlobal) {
			BLOBF obf = BLOBF.getOBF(transform.mcpTarget, TargetType.CLASS, OBFLevel.MCP);
			name = "*";
			clName = obf.getValue();
		} else {
			BLOBF obf = BLOBF.getOBF(transform.mcpTarget, transform.targetType, OBFLevel.MCP);
			name = obf.getValue();
			int lastDot = name.lastIndexOf('.');
	        clName = name.substring(0, lastDot);
	        name = name.substring(lastDot + 1, name.length());
		}
		TargetType type = transform.targetType.getBaseType();
		if (type == TargetType.METHOD) {
			name = name.split(" ")[0].replace('/', '.') + " " + name.split(" ")[1];
		}
		getTransformation(transform.directives.split(Patterns.COMMA), clName, name, type);
	}
	
	private void getTransformation(String[] changes, String clName, String name, TargetType type) {
        for (String change : changes) {
        	TargetSelector selector = type == TargetType.METHOD ? new MethodSelector(name) : new FieldSelector(name);
        	Transformation t = getTransformation(selector, change, clName);
            if (t != null) transformations.addTransformation(t);
        }
    }
    
    private Transformation getTransformation(TargetSelector selector, String change, String clName) {
    	switch (change) {
			case "+f":
			case "f": return new FinalityTransformation(selector, clName, true);
			case "-f": return new FinalityTransformation(selector, clName, false);
			case "public": return new PublicityTransformation(selector, clName, AccessLevel.PUBLIC);
			case "private": return new PublicityTransformation(selector, clName, AccessLevel.PRIVATE);
			case "protected": return new PublicityTransformation(selector, clName, AccessLevel.PROTECTED);
			case "package": return new PublicityTransformation(selector, clName, AccessLevel.PACKAGE);
	        default: System.err.println("Invalid transformation: " + change);
		}
    	return null;
    }
	
	public byte[] transform(String name, String transformedName, byte[] bytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        if (transformations.applyAll(transformedName, classNode)) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            return writer.toByteArray();
        }
        return bytes; //return original class if it was not transformed
    }
}
