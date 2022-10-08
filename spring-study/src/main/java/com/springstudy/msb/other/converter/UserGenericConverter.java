package com.springstudy.msb.other.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;

import java.util.HashSet;
import java.util.Set;

public class UserGenericConverter implements GenericConverter {

	@Autowired
	private UserService userService;

	@Override
	public Object convert(Object source, TypeDescriptor sourceType,
						  TypeDescriptor targetType) {

		if (source == null || sourceType == null || targetType == null) {
			return null;
		}

		User user = null;
		// 简单的根据原类型是Integer还是String来判断传递的原数据是id还是username，并利用UserService对应的方法返回相应的User对象
		if (sourceType.getType() == Integer.class) {
			// 根据id来查找user
			user = userService.findById((Integer) source);
		} else if (sourceType.getType() == String.class) {
			// 根据用户名来查找user
			user = userService.find((String) source);
		}
		return user;
	}

	@Override
	public Set<ConvertiblePair> getConvertibleTypes() {
		Set<ConvertiblePair> pairs = new HashSet<ConvertiblePair>();
		// 添加了两组转换的组合：Integer到User和String到User
		pairs.add(new ConvertiblePair(Integer.class, User.class));
		pairs.add(new ConvertiblePair(String.class, User.class));
		return pairs;
	}

}  