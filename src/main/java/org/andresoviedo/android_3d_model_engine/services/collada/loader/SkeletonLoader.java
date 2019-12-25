package org.andresoviedo.android_3d_model_engine.services.collada.loader;

import android.opengl.Matrix;
import android.util.Log;

import org.andresoviedo.android_3d_model_engine.services.collada.entities.JointData;
import org.andresoviedo.android_3d_model_engine.services.collada.entities.SkeletonData;
import org.andresoviedo.android_3d_model_engine.services.collada.entities.SkinningData;
import org.andresoviedo.util.math.Math3DUtils;
import org.andresoviedo.util.xml.XmlNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SkeletonLoader {

	private final XmlNode rootNode;

	private final XmlNode geometries;

	private final XmlNode visualScene;

	private final Map<String,SkinningData> skinningDataMap;

	private SkinningData skinningData;

	private List<String> boneOrder;

	private int jointCount = 0;
	private boolean jointFound = false;

	public SkeletonLoader(XmlNode rootNode, Map<String,SkinningData> skinningDataMap) {
		this.rootNode = rootNode;
		this.visualScene = rootNode.getChild("library_visual_scenes").getChild("visual_scene");
		this.geometries = rootNode.getChild("library_geometries");
		this.skinningDataMap = skinningDataMap;
	}

	// <visual_scene>
	public SkeletonData extractBoneData(){

		Log.i("SkeletonLoader", "Loading skeleton...");
		if (this.skinningDataMap != null && skinningDataMap.size() > 0) {
			skinningData = this.skinningDataMap.values().iterator().next();
			this.boneOrder = skinningData.jointOrder;
		} else{
			this.boneOrder = new ArrayList<>();
		}

		// a visual scene may contain several nodes of different kinds
		List<XmlNode> nodes = visualScene.getChildren("node");
		if (nodes == null || nodes.isEmpty()){
			return null;
		}

		// int index = boneOrder.size();

		String skeletonId = visualScene.getAttribute("id");
		final JointData rootJoint = new JointData(-1, skeletonId, skeletonId,
				skeletonId, null, Math3DUtils.IDENTITY_MATRIX, Math3DUtils.IDENTITY_MATRIX, Math3DUtils.IDENTITY_MATRIX);
		/*boneOrder.add(index, skeletonId);*/
		this.jointCount = 1;  // root counts

		// analyze all nodes to get skeleton
		for (XmlNode node : nodes){

			// get first skeleton found
			JointData jointData = loadSkeleton(node, rootJoint);
			if (jointData == null) continue;
			rootJoint.addChild(jointData);
		}

		// no skeleton found at all
		if (rootJoint.children.isEmpty()){
			Log.i("SkeletonLoader", "Skeleton not found");
			return null;
		}

		Log.i("SkeletonLoader", "Skeleton found. joints: " + jointCount+", linked bones: "+boneOrder.size());

		return new SkeletonData(jointCount, boneOrder.size(), rootJoint);
	}

	private JointData loadSkeleton(XmlNode jointNode, JointData parent){
		JointData joint = createJointData(jointNode, parent);
		if (joint == null){
			return null;
		}
		// Log.i("SkeletonLoader","Joint: index "+joint.index+", name: "+joint.nameId);
		for(XmlNode childNode : jointNode.getChildren("node")){
			JointData child = loadSkeleton(childNode, joint);
			if (child == null) continue;
			joint.addChild(child);
		}
		return joint;
	}

	private JointData createJointData(XmlNode jointNode, JointData parent){

		// joint transformation initialization
        float[] matrix = null;

		// did we find any supported transformations?
		if (jointNode.getChild("matrix") != null) {
			XmlNode jointMatrix = jointNode.getChild("matrix");
			String data = jointMatrix.getData().trim();
			if (!data.equals("1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1")) {
				float[] matrix1 = Math3DUtils.parseFloat(data.split("\\s+"));
				matrix = new float[16];
				Matrix.transposeM(matrix, 0, matrix1, 0);
			}
        }

        if (jointNode.getChild("translate") != null) {
			XmlNode translateNode = jointNode.getChild("translate");
			String data = translateNode.getData().trim().replace(',', '.');
			if (!data.equals("0 0 0")) {
				float[] translate = Math3DUtils.parseFloat(data.split("\\s+"));
				if (matrix == null) {
					matrix = new float[16];
					Matrix.setIdentityM(matrix, 0);
				}
				Matrix.translateM(matrix, 0, translate[0], translate[1], translate[2]);
			}
		}

		if (jointNode.getChild("rotate") != null) {
			for (XmlNode rotateNode : jointNode.getChildren("rotate")) {
				String data = rotateNode.getData().trim();
				if (!data.equals("0 0 0 0")) {
					float[] rotate = Math3DUtils.parseFloat(data.split("\\s+"));
					if (matrix == null) {
						matrix = new float[16];
						Matrix.setIdentityM(matrix, 0);
					}
					Matrix.rotateM(matrix, 0, rotate[3], rotate[0], rotate[1], rotate[2]);
				}
			}
		}

		if (jointNode.getChild("scale") != null){
			XmlNode scaleNode = jointNode.getChild("scale");
			String data = scaleNode.getData().trim();
			if (!data.equals("1 1 1")) {
				float[] scale = Math3DUtils.parseFloat(data.replace(',', '.').split("\\s+"));
				if (matrix == null) {
					matrix = new float[16];
					Matrix.setIdentityM(matrix, 0);
				}
				Matrix.scaleM(matrix, 0, scale[0], scale[1], scale[2]);
			}
		}

		// if no transformation was found, then this is not part of the skeleton transformation
		if (matrix == null){
			matrix = Math3DUtils.IDENTITY_MATRIX;
		}

		jointCount++;

		// get node attributes
		String nodeName = jointNode.getAttribute("name");
		String nodeSid = jointNode.getAttribute("sid");
		String nodeId = jointNode.getAttribute("id");
		String geometryId = null;
		Map<String,String> materials = new HashMap<>();
		XmlNode instance_geometry_node = jointNode.getChild("instance_geometry");
		if (instance_geometry_node == null){
			instance_geometry_node = jointNode.getChild("instance_controller");
		}
		if (instance_geometry_node != null){
			if (instance_geometry_node.getAttribute("url") != null) {
				geometryId = instance_geometry_node.getAttribute("url").substring(1);
			}

			try {
				XmlNode bind_material = instance_geometry_node.getChild("bind_material");
				if (bind_material != null){
					XmlNode technique_common = bind_material.getChild("technique_common");
					if (technique_common != null){
						XmlNode instance_material = technique_common.getChild("instance_material");
						if (instance_material != null){
							String material_symbol = instance_material.getAttribute("symbol");
							String material_name = instance_material.getAttribute("target").substring(1);
							materials.put(material_symbol,material_name);
							Log.v("SkeletonLoader",String.format("Loaded material: " +
									"%s->%s",material_symbol,material_name));
						}
					}
				}
			} catch (Exception e) {
				Log.e("SkeletonLoader","Error loading material bindings... "+e.getMessage());
			}
		}

		// is this a joint bone?
		if ("JOINT".equals(jointNode.getAttribute("type"))){
			jointFound = true;
		}

		// index is only available for declared bones
		int index = boneOrder.indexOf(nodeName);
		if (index == -1) {
			// fallback to node id
			index = boneOrder.indexOf(nodeSid);
			if (index == -1) {
				index = boneOrder.indexOf(nodeId);
			}
		}

		// calculate inverse bind matrix in case it's a bone
        float[] inverseBindMatrix = Math3DUtils.IDENTITY_MATRIX;
        if (index >= 0 && skinningData.getInverseBindMatrix() != null) {
            inverseBindMatrix = new float[16];
            Matrix.transposeM(inverseBindMatrix, 0, skinningData.getInverseBindMatrix(), index * 16);
        }

		if (index == -1 && geometryId != null) {
			XmlNode linkedGeometryNode = geometries.getChildWithAttribute("geometry", "id", geometryId);
			if (linkedGeometryNode != null) {
				index = boneOrder.size();
				boneOrder.add(geometryId);
				Log.i("SkeletonLoader","Found linked geometry. "+geometryId);
			}
		}
		if (index == -1){
			//index = boneOrder.size();
			//boneOrder.add(nodeId);
			Log.i("SkeletonLoader", "Found unlinked node. " + nodeId);
		}

		float[] bindTransform = Math3DUtils.IDENTITY_MATRIX;
        if (parent.getBindTransform() != Math3DUtils.IDENTITY_MATRIX || matrix != Math3DUtils.IDENTITY_MATRIX) {
			bindTransform = new float[16];
       		Matrix.multiplyMM(bindTransform, 0, parent.getBindTransform(), 0, matrix, 0);
		}

        return new JointData(index, nodeId, nodeName, geometryId, materials, matrix, bindTransform, inverseBindMatrix);
	}
}
