package org.odk.collect.android.utilities;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jyri Soppela on 20/03/18.
 */

public class XmlStreamParser {
    private static final String ns = null;

    public List parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readFeed(parser, "xform");
        } finally {
            in.close();
        }
    }

    private List readFeed(XmlPullParser parser, String xmlTag) throws XmlPullParserException, IOException {
        List entries = new ArrayList();

        parser.require(XmlPullParser.START_TAG, ns, "feed");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals(xmlTag)) {
                entries.add(readMD5Sum(parser));
            } else {
                skip(parser);
            }
        }
        return entries;
    }

    private String readMD5Sum(XmlPullParser parser)  throws XmlPullParserException, IOException {
        return "";
    }



}
