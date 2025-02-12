package com.demod.factorio;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

public class TypeHierarchy {
	private final Map<String, String> parents = new HashMap<>();
	private final Set<String> roots = new LinkedHashSet<>();

	public TypeHierarchy(JSONObject json) {
		Utils.forEach(json, (String name, JSONObject j) -> {
			roots.add(name);
			initParent(name, j);
		});
	}

	public Map<String, String> getParents() {
		return parents;
	}

	public Set<String> getRoots() {
		return roots;
	}

	private void initParent(String parent, JSONObject json) {
		Utils.forEach(json, (String name, JSONObject j) -> {
			parents.put(name, parent);
			initParent(name, j);
		});
	}

	public boolean isAssignable(String type, String subType) {
		String checkType = subType;
		while (checkType != null) {
			if (type.equals(checkType)) {
				return true;
			}
			checkType = parents.get(checkType);
		}
		return false;
	}
}
