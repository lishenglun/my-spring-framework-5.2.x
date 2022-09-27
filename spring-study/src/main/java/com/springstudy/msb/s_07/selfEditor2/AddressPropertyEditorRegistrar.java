package com.springstudy.msb.s_07.selfEditor2;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 地址属性编辑器的注册器
 * @date 2022/4/15 11:17 下午
 */
public class AddressPropertyEditorRegistrar implements PropertyEditorRegistrar {

	/**
	 * 注册自定义编辑器
	 */
	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry/* registry = BeanWrapperImpl */) {
		registry.registerCustomEditor(Address.class, new AddressPropertyEditor());
	}

}