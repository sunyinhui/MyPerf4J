package cn.myperf4j.asm.aop.pkg;

import cn.myperf4j.core.config.ProfilingConfig;
import cn.myperf4j.core.config.ProfilingFilter;
import cn.myperf4j.core.util.Logger;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by LinShunkang on 2018/4/15
 */
public class PackageClassAdapter extends ClassVisitor implements Opcodes {

    private String innerClassName;

    /**
     * 是否把方法体包进try-finally块；也就是当方法内抛异常的时候是否还记录响应时间；
     */
    private boolean addTryCatch;

    private boolean isInterface;

    private List<String> fieldNameList = new ArrayList<>();

    public PackageClassAdapter(final ClassVisitor cv, String innerClassName, boolean addTryCatch) {
        super(ASM5, cv);

        this.innerClassName = innerClassName;
        this.addTryCatch = addTryCatch;
    }

    public PackageClassAdapter(final ClassVisitor cv, String innerSimpleClassName) {
        this(cv, innerSimpleClassName, false);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        Logger.debug("PackageClassAdapter.visit(" + version + ", " + access + ", " + name + ", " + signature + ", " + superName + ", " + Arrays.toString(interfaces) + ")");

        super.visit(version, access, name, signature, superName, interfaces);
        this.isInterface = (access & ACC_INTERFACE) != 0;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        String upFieldName = name.substring(0, 1).toUpperCase() + name.substring(1, name.length());
        fieldNameList.add("get" + upFieldName);
        fieldNameList.add("set" + upFieldName);
        fieldNameList.add("is" + upFieldName);

        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {
        Logger.debug("PackageClassAdapter.visitMethod(" + access + ", " + name + ", " + desc + ", " + signature + ", " + Arrays.toString(exceptions) + ")");
        if (isInterface || !isNeedVisit(access, name)) {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }

        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        if (mv == null) {
            return null;
        }

        if (addTryCatch) {
            return new PackageTryCatchMethodVisitor(access, name, desc, mv, innerClassName);
        } else {
            return new PackageSimpleMethodVisitor(access, name, desc, mv, innerClassName);
        }
    }

    private boolean isNeedVisit(int access, String name) {
        //不对私有方法进行注入
        if ((access & ACC_PRIVATE) != 0 && ProfilingConfig.getInstance().isExcludePrivateMethod()) {
            return false;
        }

        //不对抽象方法和native方法进行注入
        if ((access & ACC_ABSTRACT) != 0 || (access & ACC_NATIVE) != 0) {
            return false;
        }

        if ("<init>".equals(name) || "<clinit>".equals(name)) {
            return false;
        }

        if (fieldNameList.contains(name) || ProfilingFilter.isNotNeedInjectMethod(name)) {
            return false;
        }

        return true;
    }
}
