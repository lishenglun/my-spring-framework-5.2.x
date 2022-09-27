package com.mashibing.selfEditor;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

public class AddressPropertyEditorRegistrar implements PropertyEditorRegistrar {

    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry/* registry = BeanWrapperImpl */) {
        registry.registerCustomEditor(Address.class,new AddressPropertyEditor());
    }
}
