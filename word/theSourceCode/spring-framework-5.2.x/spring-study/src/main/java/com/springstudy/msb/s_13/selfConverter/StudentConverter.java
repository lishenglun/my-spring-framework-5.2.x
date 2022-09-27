package com.springstudy.msb.s_13.selfConverter;

import org.springframework.core.convert.converter.Converter;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/4/30 11:23 上午
 */
public class StudentConverter implements Converter<String, Student> {

	/**
	 * 将S类型转换成T类型
	 * <p>
	 * Convert the source object of type {@code S} to target type {@code T}.
	 *
	 * @param source the source object to convert, which must be an instance of {@code S} (never {@code null})
	 * @return the converted object, which must be an instance of {@code T} (potentially {@code null})
	 * @throws IllegalArgumentException if the source cannot be converted to the desired target type
	 */
	@Override
	public Student convert(String source) {
		System.out.println("----");
		Student s = new Student();
		String[] splits = source.split("_");
		s.setId(Integer.parseInt(splits[0]));
		s.setName(splits[1]);

		return s;
	}

}