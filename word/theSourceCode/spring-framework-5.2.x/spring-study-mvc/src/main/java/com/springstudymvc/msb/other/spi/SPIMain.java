package com.springstudymvc.msb.other.spi;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description SPI：Service Provider Interface(服务提供者接口)：是一种服务发现机制，java里面提供给我们的扩展机制
 *
 *
 * 在META-INF/services目录下，创建"接口的全路径名称"文件(名称必须是的接口的全类路径名称)，然后在文件中写上接口的实现类的全类路径名
 *
 *
 * 参考：https://blog.csdn.net/fengyuyeguirenenen/article/details/123707060
 * @date 2022/7/18 12:13 上午
 */
public class SPIMain {

	/**
	 * ServiceLoader核心原理：
	 * (1)内部创建了一个迭代器{@link ServiceLoader.LazyIterator}，迭代器在进行开始迭代的时候，会首先拼接【/META-INF/services/ + 接口全限定类名】，得到"接口的全路径名称"文件路径
	 * (2)然后加载"接口的全路径名称"文件，
	 * (3)然后获取配置"接口的全路径名称"文件中配置的接口实现类的全限定类名
	 * (4)然后通过Class.forName()反射的方式进行实例化
	 */
	public static void main(String[] args) {
		/**
		 * ServiceLoader：查找服务接口实现的工具类
		 */
		// 创建一个用于加载IShout接口实现类的ServiceLoader
		ServiceLoader<IShout> shouts = ServiceLoader.load(IShout.class);
		//for (IShout s : shouts) {
		//	s.shout();
		//}
		Iterator<IShout> iterator = shouts.iterator();
		while (iterator.hasNext()){
			System.out.println(iterator.next());
		}
	}

}