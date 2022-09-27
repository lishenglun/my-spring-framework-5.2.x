package com.springstudy.msb.s_07.selfEditor;

import java.beans.PropertyEditorSupport;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 地址属性编辑器
 * @date 2022/4/15 11:18 下午
 */
public class AddressPropertyEditor extends PropertyEditorSupport {

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		String[] s = text.split("_");
		Address address = new Address();
		address.setProvince(s[0]);
		address.setCity(s[1]);
		address.setArea(s[2]);
		setValue(address);
	}

}