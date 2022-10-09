package com.springstudy.msb.other.converter.GenericConverter;

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

		/* 简单的根据原类型是Integer还是String来判断传递的原数据是id还是username，并利用UserService对应的方法返回相应的User对象 */
		// 如果原始类型是Integer，就假设为是id，所以根据id来查找user
		if (sourceType.getType() == Integer.class) {
			user = userService.findById((Integer) source);
		}
		// 如果原始类型是String，就假设为是username，所以根据用户名来查找user
		else if (sourceType.getType() == String.class) {
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