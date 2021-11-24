package org.bigbase.carrot;

import org.bigbase.carrot.compression.Codec;
import org.bigbase.carrot.compression.CodecFactory;
import org.bigbase.carrot.compression.CodecType;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

@RunWith(Parameterized.class)
public class CarrotCoreBase {

  protected static Codec codec;

  // static {
  //  UnsafeAccess.debug = true;
  // }

  public CarrotCoreBase(Object c) throws IOException {
    codec = (Codec) c;
  }

  @Parameterized.Parameters(name = "Run with codec={0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {CodecFactory.getInstance().getCodec(CodecType.NONE)},
          {CodecFactory.getInstance().getCodec(CodecType.LZ4)}
          //          {CodecFactory.getInstance().getCodec(CodecType.LZ4HC)}
        });
  }

  protected String getParameters() {
    return String.format(
        " with parameters: ['codec': '%s']",
        Objects.nonNull(codec) ? codec.getType().name() : "none");
  }
}
