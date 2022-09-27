package com.springstudymvc.msb.mvc_06.bridge;

/**
 * 1、桥接方法
 *
 * 桥接方法是jdk1.5之后引入泛型之后，为了使java的泛型方法生成的字节码和1.5版本前的字节码相兼容，由编译器自动生成的方法（为了跟1.5之前的代码进行兼容，所存在这个东西）
 *
 * 题外：可以通过Method.isBridge()来判断一个方法是否是桥接方法
 * 题外：桥接方法都是合成方法
 * 题外：泛型存在的价值：1、类型检查、类型限制。
 * >>> 之前定义一个List，是没有对应的类型检查，可以随意往里面放各种类型的对象，从而导致，在调用过程中，如果我想操作某一类型的数据，
 * >>> 则需要遍历所有的集合元素，挨个判断是不是对应的类型，类型不匹配，不做任何操作；类型匹配的话，我再做对应的操作！
 *
 * 2、什么时候编译器会生成桥接方法呢？
 *
 * 一个子类在继承或实现一个父类的泛型方法时，子类中明确指定了泛型类型。那么在编译时，编译器会自动生成桥接方法。
 *
 * 大家可以通过【javap -verbose SubClass.class】来查看字节码文件，在字节码中桥接方法会被标记为ACC_BRIDGE和ACC_SYNTHETIC
 * >>> ACC_BRIDGE说明这个方法是由编译生成的桥接方法
 * >>> ACC_SYNTHETIC说明这个方法是由编译器生成的，并不会在源代码中出现
 *
 * 在此方法中只声明了一个方法，但是从字节码中可以看到三个方法，
 * 第一个是无参的构造方法，编译器自动生成
 * 第二个是实现接口中的方法
 * 第三个是编译器自动生成的桥接方法，可以看到那两个标志，同时方法的参数和返回值都是Object类型
 *
 * 3、为什么要生成桥接方法呢？
 *
 * 在1.5版本之前创建一个集合对象之后，可以向其中放置任何类型的元素，无法确定和限制具体的类型，而引入泛型之后可以指定存放什么类型的数据，
 * 泛型在此处起到的作用就是检查向集合中添加的对象类型是否匹配泛型类型，如果不正常，那么在编译的时候就会出错，而不必等到运行时才发现错误。
 *
 * 但是泛型是在1.5之后的版本中出现的，为了向前兼容，所以会在编译时去掉泛型(但是可以通过反射API来获取泛型的信息)。
 *
 * 在编译时可以通过泛型来保证类型的正确性，而不必等到运行时才发现类型不正确，⚠️正是由于java泛型的擦除特性，如果不生成桥接方法，那么与1.5之前的字节码就不兼容了。
 * 因为有了继承关系，如果不生成桥接方法，那么SubClass就没有实现接口中声明的方法，语义就不正确了，所以编译器才会生成桥接方法来保证兼容性（兼容1.5版本之前的代码）
 *
 * ⚠️总结：是因为引入泛型，和泛型擦除特性，导致的需要兼容1.5版本之前的代码，所以要生成桥接方法。
 * >>> java泛型，在编译期会进行泛型擦除，例如：没有指定某个具体的类型，则用Object作为原始类型，也就相当于泛型方法编译后是一个"Object类型，入参的方法"，
 * >>> 但是子类中指定了别的泛型类型，就导致，子类中并没有实现接口中声明的"Object类型，入参的方法"，这就违背了java语义了，
 * >>> 比如：(1)实现接口时，子类必须实现接口中的方法；(2)子类必须存在该类型的方法，子类才能调用该类型的方法。
 * >>> 1.5版本之前的java代码，都是遵守这些规则的。
 * >>> 所以为了弥补语义的正确性，编译器在生成字节码的时候，就会自动为字节码文件生成一个"Object类型，入参的方法"，来保证语义的正确性，兼容1.5版本之前的代码。
 * (简单总结：java会进行泛型擦除，如果子类中指定了别的泛型类型，那么子类当中就没有重写"泛型擦除后的父类方法"，违背语义，无法兼容1.5版本之前的代码，所以编译器就在字节码文件中加入了桥接方法，也就是加入了重写的"泛型擦除后的父类方法")
 *
 * 题外：java泛型的擦除特性：JVM并不知道泛型的存在，因为泛型在编译阶段就已经被处理成普通的类和方法；处理机制是通过类型擦除，擦除规则：
 * >>> 若泛型类型没有指定具体类型，用Object作为原始类型；
 * >>> 若有限定类型< T exnteds XClass >，使用XClass作为原始类型；
 * >>> 若有多个限定< T exnteds XClass1 & XClass2 >，使用第一个边界类型XClass1作为原始类型；
 *
 * 4、实验
 * （1）在声明SuperClass类型的变量时，不指定泛型类型，那么在方法调用时就可以传任何类型的参数，因为SuperClass中的方法参数实际上是Object类型，而且
 * 编译器也不能够发现错误，但是当运行的时候就会发现参数类型不是Subclass声明的类型，会抛出类型转换异常(ClassCastException)，因为此时调用的是桥接方法，而在桥接方法中会
 * 进行类型强制转换，所以会抛出此异常
 *
 * 示范：
 * SuperClass superClass = new SubClass();
 * System.out.println(superClass.method("123"));
 * System.out.println(superClass.method(new Object()));
 * System.out.println(superClass.method(100);
 *
 * （2）如果在声明SuperClass类型的变量时，直接就指定泛型的类型，如果传入的参数类型不对，那么在编译的时候就会出现异常，这样的话就可以把错误提前得知。
 *
 * 示范：
 * SuperClass<String> superClass2 = new SubClass();
 */
public class SubClass implements SuperClass<String> {

    @Override
    public String method(String param) {
        return param;
    }

    public static void main(String[] args) {
		/*

		1、SubClass指定了泛型类型，只能传入String类型的值，所以调用superClass.method(new Object())、System.out.println(subClass.method(100)的时候，编译期，语法检查不通过！

		 */
		SubClass subClass = new SubClass();
		System.out.println(subClass.method("123"));
		// 编译期就报错
		// System.out.println(subClass.method(new Object()));
		// System.out.println(subClass.method(100);

		/*

		2、改成SuperClass后，虽然是父类引用，指向子类的对象，但是父类里面没有限制具体类型，没有类型要求的，
		所以，调用superClass.method("123")、superClass.method(new Object())、System.out.println(subClass.method(100)的时候，
		编译期，根据语法规则，语法检查要通过。

		问题是，superClass.method("123")可以调到子类SubClass的方法，入参类型就是String，所以语法检查可以通过；
		但是superClass.method(new Object())、System.out.println(subClass.method(100)是无法调到子类SubClass的方法，
		SubClass中没有该类型的方法，那是怎么通过语法检查的呢？

		如果我想让这些代码通过语法检查，意味着我对应的字节码它要生成什么对应的匹配类型方法？

		所以为了让这些代码通过语法检查，让它不报错，编译器在生成SubClass字节码文件的时候，生成了一个Object参数类型的method(Object obj)方法，
		用这么一个兼容的类型的方法来进行相关的匹配，保证我们在写代码的时候不报错 —— 编译器自动生成的方法

		当我的编译器自动生成这个Object方法之后，则能保证，我不管传递什么样的参数，都能够适配到Object，当能适配之后，就意味着编码没问题了

		该方法不是我们自己生成的，是为了方便，我们在调用过程中可以传入任意对象，不报错，引入的东西

		⚠️总结：当父类引用，指向子类对象的时候。为了让，父类引用可以调用泛型方法，通过语法检查，编译期不报错；所以编译器在生成字节码的时候，
		自动的为其子类，多生成了一个Object参数类型的方法，保证我们在进行写代码的时候不报错。属于编译器自动生成的方法。

		题外：虽然编译期的异常没有了，但是运行期的时候会出现该异常！
		题外：如果是报"缺少泛型类SuperClass<T>的类型参数，其中, T是类型变量:T扩展已在接口 SuperClass中声明的Object"这个错误，这个错误是当前spring环境的检查错误。换到一个自己项目中执行即可。

		 */
        //SuperClass superClass = new SubClass();
        //System.out.println(superClass.method("123"));
        //System.out.println(superClass.method(new Object()));
		//System.out.println(superClass.method(100));

        /*

        3、上面的这种情况，怎么才能让父类引用调用泛型方法的时候，传入的类型不对，在编译期就报异常呢，而不用到运行期才出现异常

        答：指定泛型

         */
		SuperClass<String> superClass2 = new SubClass();
		System.out.println(superClass2.method("123"));
		// 编译期报错
		//System.out.println(superClass2.method(new Object()));
		//System.out.println(superClass2.method(100);
    }

}
