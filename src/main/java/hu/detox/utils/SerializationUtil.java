package hu.detox.utils;

import hu.detox.Agent;
import hu.detox.io.IOHelper;
import org.apache.http.client.utils.CloneUtils;

import java.io.*;

@SuppressWarnings("unchecked")
public class SerializationUtil {
    public static final String SER = ".ser";
    private static final String EXT = ".ext";

    public static <T> T deepClone(final T arg) {
        if (arg == null) {
            return null;
        }
        try {
            final ByteArrayOutputStream sobj = new ByteArrayOutputStream();
            T ret = null;
            try (final ObjectOutputStream oos = new ObjectOutputStream(sobj)) {
                if (arg instanceof Externalizable && arg instanceof Cloneable) {
                    ret = (T) CloneUtils.clone(arg);
                    try {
                        ((Externalizable) ret).writeExternal(oos);
                    } finally {
                        oos.flush();
                    }
                } else {
                    oos.writeObject(arg);
                }
            }
            try (final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(sobj.toByteArray()))) {
                if (ret != null) {
                    ((Externalizable) ret).readExternal(ois);
                } else {
                    ret = (T) ois.readObject();
                }
                return ret;
            }
        } catch (final CloneNotSupportedException | ClassNotFoundException e) {
            throw new IllegalStateException("Implementation error on " + arg, e);
        } catch (final IOException e) {
            throw new IllegalStateException("Default deepclone IO error on " + arg, e);
        }
    }

    public static <T> T deserialize(final InputStream is, final boolean ext) throws IOException {
        try (final ObjectInputStream ois = new ObjectInputStream(is)) {
            if (ext) {
                final Externalizable ret = ((Class<? extends Externalizable>) ois.readObject()).newInstance();
                ret.readExternal(ois);
                return (T) ret;
            } else {
                return (T) ois.readObject();
            }
        } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            throw new IOException("Can't recreate", ex);
        }
    }

    public static void serialize(final File to, final Object ser) throws IOException {
        final OutputStream os = IOHelper.getOutput(to);
        SerializationUtil.serialize(os, ser);
    }

    public static void serialize(final OutputStream os, final Object ser) throws IOException {
        if (ser == null) {
            return;
        }
        try (final ObjectOutputStream oos = new ObjectOutputStream(os)) {
            if (ser instanceof Externalizable) {
                oos.writeObject(ser.getClass());
                ((Externalizable) ser).writeExternal(oos);
            } else {
                oos.writeObject(ser);
            }
        }
    }

    public static void serialize(final String name, final Object ser) throws IOException {
        final File saveSer = new File(Agent.WORK, name + SerializationUtil.SER);
        final File saveExt = new File(Agent.WORK, name + SerializationUtil.EXT);
        if (ser == null) {
            saveSer.delete();
            saveExt.delete();
        } else {
            SerializationUtil.serialize(ser instanceof Externalizable ? saveExt : saveSer, ser);
        }
    }

    public static void store(final Object ser) throws IOException {
        if (ser == null) {
            return;
        }
        final File save = new File(Agent.WORK, ser.getClass().getName() + (ser instanceof Externalizable ? SerializationUtil.EXT : SerializationUtil.SER));
        SerializationUtil.serialize(save, ser);
    }

    private SerializationUtil() {
        // Util class
    }

}
