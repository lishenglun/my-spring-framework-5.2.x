package com.springstudymvc.msb.other.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 转换器
 *
 *
 * 注意；
 *
 * 题外：
 *
 *
 * @date 2022/10/8 09:32
 */
public class StringToDateConverter implements Converter<String, Date> {

	/**
	 * 用于把 String 类型转成日期类型
	 */
	@Override
	public Date convert(String source) {
		DateFormat format = null;
		try {
			if (StringUtils.isEmpty(source)) {
				throw new NullPointerException("请输入要转换的日期");
			}
			format = new SimpleDateFormat("yyyy-MM-dd");
			Date date = format.parse(source);
			return date;
		} catch (Exception e) {
			throw new RuntimeException("输入日期有误");
		}
	}

}