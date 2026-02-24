package hu.detox.parsers;

import com.google.gson.JsonElement;
import hu.detox.io.FileUtils;
import hu.detox.utils.strings.StringUtils;
import hu.detox.utils.reflection.ReflectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.lang3.ArrayUtils;
import org.dom4j.*;
import org.dom4j.CharacterData;
import org.dom4j.io.*;
import org.dom4j.tree.DefaultAttribute;
import org.dom4j.tree.DefaultText;
import org.dom4j.util.NodeComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.tidy.Tidy;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.*;

//CHOFF TODO This is a lenghty class now
public class XmlUtils {
    private static final Logger logger = LoggerFactory.getLogger(XmlUtils.class);
    public static final Tidy XML_TIDY = new Tidy();
    public static final char[] BIDI = new char[]{'\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068', '\u2069', '\u200E', '\u200F',
            '\u061C'};
    public static final String[] BIDI_STR = new String[XmlUtils.BIDI.length];
    private static final String[] BIDI_TO = new String[XmlUtils.BIDI.length];

    static {
        XmlUtils.XML_TIDY.setOutputEncoding(hu.detox.utils.SystemUtils.UTF8);
        XmlUtils.XML_TIDY.setInputEncoding(hu.detox.utils.SystemUtils.UTF8);
        XmlUtils.XML_TIDY.setXmlOut(true);
        XmlUtils.XML_TIDY.setXmlTags(true);
        XmlUtils.XML_TIDY.setQuiet(true);
        XmlUtils.XML_TIDY.setXmlSpace(true);
        XmlUtils.XML_TIDY.setIndentContent(false);
        XmlUtils.XML_TIDY.setIndentCdata(false);
        XmlUtils.XML_TIDY.setSmartIndent(false);
        XmlUtils.XML_TIDY.setForceOutput(true);
        XmlUtils.XML_TIDY.setWraplen(Integer.MAX_VALUE);
        int i = 0;
        for (final char c : XmlUtils.BIDI) {
            XmlUtils.BIDI_STR[i] = "" + c;
            XmlUtils.BIDI_TO[i] = "&#" + (int) c + ";";
            i++;
        }
    }

    public static final EntityResolver ENTITIES = new EntityResolver() {
        @Override
        public InputSource resolveEntity(final String publicId, String systemId) throws SAXException, IOException {
            if (!FilenameUtils.isExtension(systemId, new String[]{"dtd"})) {
                return null;
            }
            systemId = "/" + XmlUtils.class.getPackage().getName().replace(".", "/") + "/xml/" + FilenameUtils.getName(systemId);
            //systemId = "xml/" + FilenameUtils.getName(systemId);
            InputStream is = XmlUtils.class.getResourceAsStream(systemId);
            if (is == null) {
                is = new ByteArrayInputStream(new byte[0]);
            }
            return new InputSource(is);
        }
    };

    public static String escapeBidi(final String str) {
        return StringUtils.replaceEach(str, XmlUtils.BIDI_STR, XmlUtils.BIDI_TO);
    }

    public static void escapeXmlMinimal(final CharSequence xml, final Writer w) throws IOException {
        for (int i = 0; i < xml.length(); i++) {
            final char c = xml.charAt(i);
            if (c == '"') {
                w.write("&quot;");
            } else if (c == '\'') {
                w.write("&apos;");
            } else if (c == '<') {
                w.write("&lt;");
            } else {
                final int idx = ArrayUtils.indexOf(XmlUtils.BIDI, c);
                if (idx <= ArrayUtils.INDEX_NOT_FOUND) {
                    w.write(c);
                } else {
                    w.write(XmlUtils.BIDI_TO[idx]);
                }
            }
        }
    }

    public static String escapeXmlMinimal(final String xml) {
        final StringWriter sw = new StringWriter();
        try {
            XmlUtils.escapeXmlMinimal(xml, sw);
        } catch (final IOException e) { //NOCH Never happens
        }
        return sw.toString();
    }

    public static String xpathOf(final Node node) {
        final StringBuilder sb = new StringBuilder();
        XmlUtils.xpathOf(node, sb);
        return sb.toString();
    }

    public static void xpathOf(Node node, final StringBuilder sb) {
        if (node instanceof Attribute) {
            sb.insert(0, '@');
        }
        if (node instanceof Element) {
            sb.insert(0, "[" + XmlUtils.xpathParentIndexOf(node) + ']');
        }
        sb.insert(0, node.getName());
        node = node.getParent();
        sb.insert(0, '/');
        if (node != null) {
            XmlUtils.xpathOf(node, sb);
        }
    }

    public static int xpathParentIndexOf(final Node node) {
        return XmlUtils.xpathParentIndexOf(node, true);
    }

    public static int xpathParentIndexOf(final Node node, final boolean myNameCountsOnly) {
        int idx = 1;
        final Element par = node.getParent();
        if (par != null) {
            for (final Element e : (List<Element>) par.elements()) {
                if (e.equals(node)) {
                    break;
                }
                if (myNameCountsOnly) {
                    if (e.getName().equals(node.getName())) {
                        idx++;
                    }
                } else {
                    idx++;
                }
            }
        }
        return idx;
    }

    private transient SAXReader reader = new SAXReader();
    private transient DOMReader domReader = new DOMReader();
    private transient DOMWriter domWriter = new DOMWriter();
    public transient DocumentFactory factory = DocumentFactory.getInstance();
    private final DocumentBuilder w3cDocBuilder;
    private transient TransformerFactory transformers = TransformerFactory.newInstance();
    public transient OutputFormat compact = OutputFormat.createCompactFormat();
    public transient OutputFormat pretty = OutputFormat.createPrettyPrint();
    public transient NodeComparator comparator = new NodeComparator();
    private transient Node node;
    private boolean tidy;
    private transient boolean tidied;

    public XmlUtils() {
        this.init();
        this.pretty.setIndentSize(4);
        try {
            this.w3cDocBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (final ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public int add(final Collection<Node> nodes) {
        return this.add(nodes, false);
    }

    public int add(final Collection<Node> nodes, final boolean detach) {
        int ret = 0;
        final Element p = (Element) this.node;
        for (Node n : nodes) {
            if (n == null) {
                continue;
            }
            ret++;
            if (detach) {
                n.detach();
            } else if (n.getParent() != null) {
                n = (Node) n.clone();
            }
            p.add(n);
        }
        return ret;
    }

    public int add(final Node... nodes) {
        return this.add(Arrays.asList(nodes));
    }

    public boolean equals(final Collection<Node> ns1, final Collection<Node> ns2) {
        boolean ret = ns1.size() == ns2.size();
        if (ret) {
            final Iterator<Node> ni1 = ns1.iterator();
            final Iterator<Node> ni2 = ns2.iterator();
            while (ni1.hasNext() && ni2.hasNext()) {
                if (this.comparator.compare(ni1.next(), ni2.next()) != 0) {
                    ret = false;
                    break;
                }
            }
        }
        return ret;
    }

    public <T extends Node> T getExistingUnder(final String spec) {
        final Element par = (Element) this.node;
        Node ret = par.selectSingleNode(spec);
        if (ret == null) {
            ret = par;
            for (String sp : spec.split("/", -1)) {
                if (org.apache.commons.lang3.StringUtils.isEmpty(sp)) {
                    ret = ret.getDocument();
                } else {
                    if (ret instanceof Attribute) {
                        ret = ret.getParent();
                    }
                    Element under = (Element) ret.selectSingleNode(sp);
                    if (under == null) {
                        under = (Element) ret;
                        sp = sp.replaceFirst("\\[.+", ""); // Remove any query
                        if (sp.startsWith("@")) {
                            final DefaultAttribute att = new DefaultAttribute(sp.substring(1), "");
                            under.add(att);
                            ret = att;
                        } else {
                            ret = under.addElement(sp);
                        }
                    } else {
                        ret = under;
                    }
                }
            }
        }
        return (T) ret;
    }

    public Node getNode() {
        return this.node;
    }

    private void init() {
        this.reader = new SAXReader();
        this.factory = DocumentFactory.getInstance();
        this.transformers = TransformerFactory.newInstance();
        this.compact = OutputFormat.createCompactFormat();
        this.pretty = OutputFormat.createPrettyPrint();
        this.comparator = new NodeComparator();
        this.reader.setEncoding(hu.detox.utils.SystemUtils.UTF8);
        this.reader.setEntityResolver(XmlUtils.ENTITIES);
        this.transformers.setErrorListener(new ErrorListener() {
            @Override
            public void error(final TransformerException exception) throws TransformerException {
                XmlUtils.logger.error("Error during transform", exception);
            }

            @Override
            public void fatalError(final TransformerException exception) throws TransformerException {
                XmlUtils.logger.error("Failed to transform", exception);

            }

            @Override
            public void warning(final TransformerException exception) throws TransformerException {
                XmlUtils.logger.warn("Transformer continues", exception);
            }
        });
    }

    public String innerXML() {
        final StringBuilder builder = new StringBuilder();
        for (final Node n : (Collection<Node>) ((Element) this.node).content()) {
            builder.append(n.asXML());
        }
        return builder.toString();
    }

    public boolean isTidy() {
        return this.tidy;
    }

    public String minimize() throws IOException {
        return this.output(this.compact);
    }

    public String minimize(final String xml) throws DocumentException {
        return this.output(xml, this.compact);
    }

    public void noNS() {
        this.node.accept(NameSpaceCleaner.INSTANCE);
    }

    public String output(final CharSequence xml, final OutputFormat of) throws DocumentException {
        this.read(xml);
        return this.output(of);
    }

    public String output(final OutputFormat of) {
        final StringWriter res = new StringWriter();
        try {
            this.output(of, res);
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot write to memory of " + res.getClass(), e);
        }
        return res.toString();
    }

    public void output(final OutputFormat of, final File fxml) throws IOException {
        final Writer fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fxml), this.reader.getEncoding()));
        try {
            this.output(of, fw);
        } finally {
            fw.close();
        }
    }

    public void output(final OutputFormat of, final Writer w) throws IOException {
        final XMLWriter writer = new XMLWriter(w, of == null ? this.pretty : of) {
            @Override
            protected boolean shouldEncodeChar(int codepoint) {
                final boolean sure = ArrayUtils.contains(XmlUtils.BIDI, (char) codepoint);
                return sure || super.shouldEncodeChar(codepoint);
            }
        };
        try {
            if (of == this.compact) {
                final List comments = this.node.selectNodes("//comment()");
                for (final Iterator it = comments.iterator(); it.hasNext(); ) {
                    ((org.dom4j.Comment) it.next()).detach();
                }
            }
            writer.write(this.node);
        } finally {
            writer.flush();
        }
    }

    public Document read(final byte[] xml) throws DocumentException {
        try {
            return this.readResource(new ByteArrayInputStream(xml));
        } catch (final IOException io) {
            throw new IllegalStateException("Byte array[" + xml.length + "] cannot be read", io);
        }
    }

    public Document read(final CharSequence xml) throws DocumentException {
        try {
            return this.readResource(new CharSequenceReader(xml));
        } catch (final IOException e) {
            throw new IllegalStateException(xml + " cannot be read?", e);
        }
    }

    public Document read(final Element xml) {
        final Document doc = this.factory.createDocument((Element) xml.detach());
        this.setNode(doc);
        return doc;
    }

    public Document read(final org.w3c.dom.Document xml) throws DocumentException {
        return this.domReader.read(xml);
    }

    private void readObject(final ObjectInputStream parIn) throws IOException, ClassNotFoundException {
        parIn.defaultReadObject();
        this.init();
    }

    public Document readResource(final File res) throws IOException, DocumentException {
        return this.readResource(new FileInputStream(res));
    }

    public Document readResource(final InputStream res) throws IOException, DocumentException {
        return this.readResource(new InputStreamReader(res, this.reader.getEncoding()));
    }

    public Document readResource(final Reader res) throws IOException, DocumentException {
        try {
            this.tidied = false;
            if (res.markSupported() && this.tidy) {
                res.mark(FileUtils.largeBufferSize);
            }
            final Document doc = this.reader.read(res);
            this.setNode(doc);
            return doc;
        } catch (final DocumentException de) {
            if (this.tidy && res.markSupported()) {
                res.reset();
                final org.w3c.dom.Document doc = XmlUtils.XML_TIDY.parseDOM(res, (Writer) null);
                this.tidied = true;
                return this.read(doc);
            } else {
                throw de;
            }
        } finally {
            res.close();
        }
    }

    public Document readResource(final String res) throws IOException, DocumentException {
        InputStream is = XmlUtils.class.getResourceAsStream(res);
        if (is == null) {
            is = new FileInputStream(res);
        }
        return this.readResource(is);
    }

    public Node replaceNode(final Node to) {
        final Node from = this.node;
        final int idx = from.getParent().content().indexOf(from);
        final Node ret = (Node) to.clone();
        from.getParent().content().set(idx, ret);
        from.detach();
        return ret;
    }

    public Node select(final String xpath) {
        return this.select(xpath, Node.class);
    }

    public Node select(final String xpath, final Class<? extends Node> nt) {
        return ReflectionUtils.bulk(this.node.selectNodes(xpath), nt);
    }

    public XmlUtils setNode(final Node node) {
        this.node = node;
        return this;
    }

    public XmlUtils setNode(final org.w3c.dom.Node node) {
        final org.w3c.dom.Document dd = this.w3cDocBuilder.newDocument();
        //final org.w3c.dom.Node n = dd.adoptNode(node);
        final org.w3c.dom.Node n = dd.importNode(node, true);
        dd.appendChild(n);
        this.node = this.domReader.read(dd).getRootElement();
        return this;
    }

    public void setTidy(final boolean tidy) {
        this.tidy = tidy;
    }

    public JsonElement toJson() {
        return (JsonElement) JsonXmlUtils.INSTANCE.apply(this.node);
    }

    public Document trafo(final StreamSource ss) throws TransformerException {
        final Transformer trafo = this.transformers.newTransformer(ss);
        final DocumentSource source = new DocumentSource((Document) this.node);
        final DocumentResult result = new DocumentResult();
        trafo.transform(source, result);
        this.node = result.getDocument();
        return (Document) this.node;
    }

    public static String minSimple(final String str) {
        return str.replaceAll("[\n\r\t]+", "").trim();
    }

    public XmlUtils unwrap() throws IOException {
        if (this.node instanceof Element || this.node instanceof CharacterData) {
            final Element e = this.node.getParent();
            final List<Node> ns = e.content();
            final int idx = ns.indexOf(this.node);
            if (this.node instanceof Element) {
                final List<Node> els = new LinkedList<Node>(((Element) this.node).content());
                for (final Node n : els) {
                    n.detach();
                }
                ns.addAll(idx, els);
            } else {
                final DefaultText dt = new DefaultText(this.node.getText());
                ns.add(idx, dt);
            }
            this.node.detach();
            return this;
        } else {
            throw new IllegalStateException(this.node + " is not unwrappable");
        }
    }

    public boolean wasTidied() {
        return this.tidied;
    }
}
