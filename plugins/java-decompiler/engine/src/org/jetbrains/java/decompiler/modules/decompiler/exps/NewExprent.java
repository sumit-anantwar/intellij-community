/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericClassDescriptor;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMain;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ListStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NewExprent extends Exprent {
  private InvocationExprent constructor;
  private final VarType newType;
  private List<Exprent> lstDims = new ArrayList<>();
  private List<Exprent> lstArrayElements = new ArrayList<>();
  private boolean directArrayInit;
  private boolean isVarArgParam;
  private boolean anonymous;
  private boolean lambda;
  private boolean enumConst;

  public NewExprent(VarType newType, ListStack<Exprent> stack, int arrayDim, Set<Integer> bytecodeOffsets) {
    this(newType, getDimensions(arrayDim, stack), bytecodeOffsets);
  }

  public NewExprent(VarType newType, List<Exprent> lstDims, Set<Integer> bytecodeOffsets) {
    super(EXPRENT_NEW);
    this.newType = newType;
    this.lstDims = lstDims;

    anonymous = false;
    lambda = false;
    if (newType.type == CodeConstants.TYPE_OBJECT && newType.arrayDim == 0) {
      ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value);
      if (node != null && (node.type == ClassNode.CLASS_ANONYMOUS || node.type == ClassNode.CLASS_LAMBDA)) {
        anonymous = true;
        if (node.type == ClassNode.CLASS_LAMBDA) {
          lambda = true;
        }
      }
    }

    addBytecodeOffsets(bytecodeOffsets);
  }

  private static List<Exprent> getDimensions(int arrayDim, ListStack<Exprent> stack) {
    List<Exprent> lstDims = new ArrayList<>();
    for (int i = 0; i < arrayDim; i++) {
      lstDims.add(0, stack.pop());
    }
    return lstDims;
  }

  @Override
  public VarType getExprType() {
    return anonymous ? DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value).anonymousClassType : newType;
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    CheckTypesResult result = new CheckTypesResult();

    if (newType.arrayDim != 0) {
      for (Exprent dim : lstDims) {
        result.addMinTypeExprent(dim, VarType.VARTYPE_BYTECHAR);
        result.addMaxTypeExprent(dim, VarType.VARTYPE_INT);
      }

      if (newType.arrayDim == 1) {
        VarType leftType = newType.decreaseArrayDim();
        for (Exprent element : lstArrayElements) {
          result.addMinTypeExprent(element, VarType.getMinTypeInFamily(leftType.typeFamily));
          result.addMaxTypeExprent(element, leftType);
        }
      }
    }
    else if (constructor != null) {
      return constructor.checkExprTypeBounds();
    }

    return result;
  }

  @Override
  public List<Exprent> getAllExprents() {
    List<Exprent> lst = new ArrayList<>();

    if (newType.arrayDim != 0) {
      lst.addAll(lstDims);
      lst.addAll(lstArrayElements);
    }
    else if (constructor != null) {
      Exprent constructor = this.constructor.getInstance();
      if (constructor != null) { // should be true only for a lambda expression with a virtual content method
        lst.add(constructor);
      }
      lst.addAll(this.constructor.getLstParameters());
    }

    return lst;
  }

  @Override
  public Exprent copy() {
    List<Exprent> lst = new ArrayList<>();
    for (Exprent expr : lstDims) {
      lst.add(expr.copy());
    }

    NewExprent ret = new NewExprent(newType, lst, bytecode);
    ret.setConstructor(constructor == null ? null : (InvocationExprent)constructor.copy());
    ret.setLstArrayElements(lstArrayElements);
    ret.setDirectArrayInit(directArrayInit);
    ret.setAnonymous(anonymous);
    ret.setEnumConst(enumConst);
    return ret;
  }

  @Override
  public int getPrecedence() {
    return 1; // precedence of new
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();

    if (anonymous) {
      ClassNode child = DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value);

      if (!enumConst) {
        String enclosing = null;

        if (!lambda && constructor != null) {
          enclosing = getQualifiedNewInstance(child.anonymousClassType.value, constructor.getLstParameters(), indent, tracer);
          if (enclosing != null) {
            buf.append(enclosing).append('.');
          }
        }

        buf.append("new ");

        String typename = ExprProcessor.getCastTypeName(child.anonymousClassType);
        if (enclosing != null) {
          ClassNode anonymousNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(child.anonymousClassType.value);
          if (anonymousNode != null) {
            typename = anonymousNode.simpleName;
          }
          else {
            typename = typename.substring(typename.lastIndexOf('.') + 1);
          }
        }

        GenericClassDescriptor descriptor = ClassWriter.getGenericClassDescriptor(child.classStruct);
        if (descriptor != null) {
          if (descriptor.superinterfaces.isEmpty()) {
            buf.append(GenericMain.getGenericCastTypeName(descriptor.superclass));
          }
          else {
            if (descriptor.superinterfaces.size() > 1 && !lambda) {
              DecompilerContext.getLogger().writeMessage("Inconsistent anonymous class signature: " + child.classStruct.qualifiedName,
                                                         IFernflowerLogger.Severity.WARN);
            }
            buf.append(GenericMain.getGenericCastTypeName(descriptor.superinterfaces.get(0)));
          }
        }
        else {
          buf.append(typename);
        }
      }

      buf.append('(');

      if (!lambda && constructor != null) {
        List<Exprent> parameters = constructor.getLstParameters();
        List<VarVersionPair> mask = child.getWrapper().getMethodWrapper(CodeConstants.INIT_NAME, constructor.getStringDescriptor()).synthParameters;
        if (mask == null) {
          InvocationExprent superCall = child.superInvocation;
          mask = ExprUtil.getSyntheticParametersMask(superCall.getClassname(), superCall.getStringDescriptor(), parameters.size());
        }

        int start = enumConst ? 2 : 0;
        boolean firstParam = true;
        for (int i = start; i < parameters.size(); i++) {
          if (mask == null || mask.get(i) == null) {
            if (!firstParam) {
              buf.append(", ");
            }

            ExprProcessor.getCastedExprent(parameters.get(i), constructor.getDescriptor().params[i], buf, indent, true, tracer);

            firstParam = false;
          }
        }
      }

      buf.append(')');

      if (enumConst && buf.length() == 2) {
        buf.setLength(0);
      }

      if (lambda) {
        if (!DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS)) {
          buf.setLength(0);  // remove the usual 'new <class>()', it will be replaced with lambda style '() ->'
        }
        Exprent methodObject = constructor == null ? null : constructor.getInstance();
        TextBuffer clsBuf = new TextBuffer();
        new ClassWriter().classLambdaToJava(child, clsBuf, methodObject, indent, tracer);
        buf.append(clsBuf);
        tracer.incrementCurrentSourceLine(clsBuf.countLines());
      }
      else {
        TextBuffer clsBuf = new TextBuffer();
        new ClassWriter().classToJava(child, clsBuf, indent, tracer);
        buf.append(clsBuf);
        tracer.incrementCurrentSourceLine(clsBuf.countLines());
      }
    }
    else if (directArrayInit) {
      VarType leftType = newType.decreaseArrayDim();
      buf.append('{');
      for (int i = 0; i < lstArrayElements.size(); i++) {
        if (i > 0) {
          buf.append(", ");
        }
        ExprProcessor.getCastedExprent(lstArrayElements.get(i), leftType, buf, indent, false, tracer);
      }
      buf.append('}');
    }
    else if (newType.arrayDim == 0) {
      if (!enumConst) {
        String enclosing = null;

        if (constructor != null) {
          enclosing = getQualifiedNewInstance(newType.value, constructor.getLstParameters(), indent, tracer);
          if (enclosing != null) {
            buf.append(enclosing).append('.');
          }
        }

        buf.append("new ");

        String typename = ExprProcessor.getTypeName(newType);
        if (enclosing != null) {
          ClassNode newNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(newType.value);
          if (newNode != null) {
            typename = newNode.simpleName;
          }
          else {
            typename = typename.substring(typename.lastIndexOf('.') + 1);
          }
        }
        buf.append(typename);
      }

      if (constructor != null) {
        List<Exprent> parameters = constructor.getLstParameters();
        List<VarVersionPair> mask = ExprUtil.getSyntheticParametersMask(constructor.getClassname(), constructor.getStringDescriptor(), parameters.size());

        int start = enumConst ? 2 : 0;
        if (!enumConst || start < parameters.size()) {
          buf.append('(');

          boolean firstParam = true;
          for (int i = start; i < parameters.size(); i++) {
            if (mask == null || mask.get(i) == null) {
              Exprent expr = InvocationExprent.unboxIfNeeded(parameters.get(i));
              VarType leftType = constructor.getDescriptor().params[i];

              if (i == parameters.size() - 1 && expr.getExprType() == VarType.VARTYPE_NULL && probablySyntheticParameter(leftType.value)) {
                break;  // skip last parameter of synthetic constructor call
              }

              if (!firstParam) {
                buf.append(", ");
              }

              ExprProcessor.getCastedExprent(expr, leftType, buf, indent, true, false, true, tracer);

              firstParam = false;
            }
          }

          buf.append(')');
        }
      }
    }
    else if (isVarArgParam) {
      // just print the array elements
      VarType leftType = newType.decreaseArrayDim();
      for (int i = 0; i < lstArrayElements.size(); i++) {
        if (i > 0) {
          buf.append(", ");
        }

        // new String[][]{{"abc"}, {"DEF"}} => new String[]{"abc"}, new String[]{"DEF"}
        Exprent element = lstArrayElements.get(i);
        if (element.type == EXPRENT_NEW) {
          ((NewExprent) element).setDirectArrayInit(false);
        }
        ExprProcessor.getCastedExprent(element, leftType, buf, indent, false, tracer);
      }

      // if there is just one element of Object[] type it needs to be casted to resolve ambiguity
      if (lstArrayElements.size() == 1) {
        VarType elementType = lstArrayElements.get(0).getExprType();
        if (elementType.type == CodeConstants.TYPE_OBJECT && elementType.value.equals("java/lang/Object") && elementType.arrayDim >= 1) {
          buf.prepend("(Object)");
        }
      }
    }
    else {
      buf.append("new ").append(ExprProcessor.getTypeName(newType));

      if (lstArrayElements.isEmpty()) {
        for (int i = 0; i < newType.arrayDim; i++) {
          buf.append('[');
          if (i < lstDims.size()) {
            buf.append(lstDims.get(i).toJava(indent, tracer));
          }
          buf.append(']');
        }
      }
      else {
        for (int i = 0; i < newType.arrayDim; i++) {
          buf.append("[]");
        }

        VarType leftType = newType.decreaseArrayDim();
        buf.append('{');
        for (int i = 0; i < lstArrayElements.size(); i++) {
          if (i > 0) {
            buf.append(", ");
          }
          ExprProcessor.getCastedExprent(lstArrayElements.get(i), leftType, buf, indent, false, tracer);
        }
        buf.append('}');
      }
    }

    return buf;
  }

  private static boolean probablySyntheticParameter(String className) {
    ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(className);
    return node != null && node.type == ClassNode.CLASS_ANONYMOUS;
  }

  private static String getQualifiedNewInstance(String classname, List<Exprent> lstParams, int indent, BytecodeMappingTracer tracer) {
    ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(classname);

    if (node != null && node.type != ClassNode.CLASS_ROOT && node.type != ClassNode.CLASS_LOCAL
        && (node.access & CodeConstants.ACC_STATIC) == 0) {
      if (!lstParams.isEmpty()) {
        Exprent enclosing = lstParams.get(0);

        boolean isQualifiedNew = false;

        if (enclosing.type == Exprent.EXPRENT_VAR) {
          VarExprent varEnclosing = (VarExprent)enclosing;

          StructClass current_class = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE)).classStruct;
          String this_classname = varEnclosing.getProcessor().getThisVars().get(new VarVersionPair(varEnclosing));

          if (!current_class.qualifiedName.equals(this_classname)) {
            isQualifiedNew = true;
          }
        }
        else {
          isQualifiedNew = true;
        }

        if (isQualifiedNew) {
          return enclosing.toJava(indent, tracer).toString();
        }
      }
    }

    return null;
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == constructor) {
      constructor = (InvocationExprent)newExpr;
    }

    if (constructor != null) {
      constructor.replaceExprent(oldExpr, newExpr);
    }

    for (int i = 0; i < lstDims.size(); i++) {
      if (oldExpr == lstDims.get(i)) {
        lstDims.set(i, newExpr);
      }
    }

    for (int i = 0; i < lstArrayElements.size(); i++) {
      if (oldExpr == lstArrayElements.get(i)) {
        lstArrayElements.set(i, newExpr);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof NewExprent)) return false;

    NewExprent ne = (NewExprent)o;
    return InterpreterUtil.equalObjects(newType, ne.getNewType()) &&
           InterpreterUtil.equalLists(lstDims, ne.getLstDims()) &&
           InterpreterUtil.equalObjects(constructor, ne.getConstructor()) &&
           directArrayInit == ne.directArrayInit &&
           InterpreterUtil.equalLists(lstArrayElements, ne.getLstArrayElements());
  }

  public InvocationExprent getConstructor() {
    return constructor;
  }

  public void setConstructor(InvocationExprent constructor) {
    this.constructor = constructor;
  }

  public List<Exprent> getLstDims() {
    return lstDims;
  }

  public VarType getNewType() {
    return newType;
  }

  public List<Exprent> getLstArrayElements() {
    return lstArrayElements;
  }

  public void setLstArrayElements(List<Exprent> lstArrayElements) {
    this.lstArrayElements = lstArrayElements;
  }

  public void setDirectArrayInit(boolean directArrayInit) {
    this.directArrayInit = directArrayInit;
  }

  public void setVarArgParam(boolean isVarArgParam) {
    this.isVarArgParam = isVarArgParam;
  }

  public boolean isLambda() {
    return lambda;
  }

  public boolean isAnonymous() {
    return anonymous;
  }

  public void setAnonymous(boolean anonymous) {
    this.anonymous = anonymous;
  }

  public void setEnumConst(boolean enumConst) {
    this.enumConst = enumConst;
  }
}
