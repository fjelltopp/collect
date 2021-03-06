package org.odk.collect.android.utilities;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities to handle XML Streams for reading form lists and manifests from ODK Aggregate
 *
 * Created by Jyri Soppela on 20/03/18.
 */

public final class XmlStreamUtils {
    private static final String ns = null;
    private static final String FORM_LIST_TAG = "xforms";
    private static final String FORM_ITEM_TAG = "xform";
    private static final String FORM_ID_TAG = "formID";
    private static final String FORM_NAME_TAG = "name";
    private static final String FORM_MD5_TAG = "hash";
    private static final String FORM_DOWNLOAD_TAG = "downloadUrl";
    private static final String MANIFEST_TAG = "manifest";
    private static final String MANIFEST_MEDIA_FILE_TAG = "mediaFile";
    private static final String MEDIA_FILE_NAME_TAG = "filename";
    private static final String MEDIA_FILE_MD5_TAG = "hash";
    private static final String MEDIA_FILE_DOWNLOAD_TAG = "downloadUrl";

    public static class XFormHeader {
        public final String formId;
        public final String name;
        public final String hash;
        public final String downloadUrl;
        public final ArrayList mediaFiles;

        private XFormHeader(String formId, String name, String hash, String downloadUrl, ArrayList mediaFiles) {
            this.formId = formId;
            this.name = name;
            this.hash = hash;
            this.downloadUrl = downloadUrl;
            this.mediaFiles = mediaFiles;
        }
    }

    public static class MediaFile {
        public final String id;
        public final String name;
        public final String md5;

        private MediaFile(String id, String name, String md5) {
            this.id = id;
            this.name = name;
            this.md5 = md5;
        }
    }

    public static List<XmlStreamUtils.XFormHeader> readFormHeaders(InputStream in)
            throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return getFormHeaders(parser);
        } finally {
            in.close();
        }
    }

    private static List getFormHeaders(XmlPullParser parser) throws XmlPullParserException, IOException {
        List forms = new ArrayList();

        parser.require(XmlPullParser.START_TAG, ns, FORM_LIST_TAG);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals(FORM_ITEM_TAG)) {
                forms.add(readFormHeader(parser));
            } else {
                skip(parser);
            }
        }
        return forms;
    }

    // Parses the contents of a form definition.
    private static XFormHeader readFormHeader(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, FORM_ITEM_TAG);
        String formId = null;
        String formName = null;
        String hash = null;
        String downloadUrl = null;
        ArrayList mediaFiles = new ArrayList();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals(FORM_ID_TAG)) {
                formId = readFormTag(parser, FORM_ID_TAG);
            } else if (tagName.equals(FORM_NAME_TAG)) {
                formName = readFormTag(parser, FORM_NAME_TAG);
            } else if (tagName.equals(FORM_MD5_TAG)) {
                hash = readFormTag(parser, FORM_MD5_TAG);
            } else if (tagName.equals(FORM_DOWNLOAD_TAG)) {
                downloadUrl = readFormTag(parser, FORM_DOWNLOAD_TAG);
            }
            else {
                skip(parser);
            }
        }
        return new XFormHeader(formId, formName, hash, downloadUrl, mediaFiles);
    }

    // Processes simple text tags in the headers.
    private static String readFormTag(XmlPullParser parser, String tag) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, tag);
        String title = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, tag);
        return title;
    }

    // Extracts text value form tags.
    private static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }


    // Skips tag and returns to higher level of hierarchy
    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

}
