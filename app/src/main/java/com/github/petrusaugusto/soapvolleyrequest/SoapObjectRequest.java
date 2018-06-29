/*
Copyright 2017 Petrus Augusto (tecozc@gmail.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.github.petrusaugusto.soapvolleyrequest;

import android.util.Xml;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;

import org.ksoap2.SoapEnvelope;
import org.ksoap2.SoapFault;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;
import org.ksoap2.transport.HttpsTransportSE;
import org.ksoap2.transport.Transport;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Objects;

public class SoapObjectRequest extends Request<SoapObject> {
    private static final int REQUEST_TIMEOUT = 10 * 1000;
    private static final int NUM_REQUEST_RETRIES = 1;
    private static final String PROTOCOL_CHARSET = "utf-8";
    private static final String PROTOCOL_CONTENT_TYPE = String.format("text/xml; charset=%s", PROTOCOL_CHARSET);

    private Map<String, Object> rArguments;
    private Response.Listener<SoapObject> mListener;
    private String soapNamespace;
    private String soapMethodName;
    private String soapAction;
    private boolean isHTTPS;
    private boolean showSoapEnvelop = false;

    public SoapObjectRequest(String url, String namespace, String methodName, String action, Map<String,Object> arguments, Response.Listener<SoapObject> mListener, Response.ErrorListener listener) {
        super(Method.POST, url, listener);
        this.rArguments = arguments;
        this.mListener = mListener;
        this.soapMethodName = methodName;
        this.soapNamespace = namespace;
        this.soapAction = action;

        // Checando o tipo do protocolo URL
        // Checking URL protocol type
        this.isHTTPS = url.startsWith("https://");

        // Definindo o RetryPolicy
        // Setting RetryPolicy
        this.setRetryPolicy(new DefaultRetryPolicy(REQUEST_TIMEOUT, NUM_REQUEST_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    public SoapObjectRequest(String url, String namespace, String methodName, Map<String,Object> arguments, Response.Listener<SoapObject> mListener, Response.ErrorListener listener) {
        super(Method.POST, url, listener);
        this.rArguments = arguments;
        this.mListener = mListener;
        this.soapMethodName = methodName;
        this.soapNamespace = namespace;

        // Gerando o SoapAction (com base no NameSpace + MethodName)
        // Auto generating Soap-ActionName (joining NameSpace + MethodName
        this.soapAction = String.format("%s#%s",soapNamespace, soapMethodName);

        // Checando o tipo do protocolo URL
        // Checking URL protocol type
        this.isHTTPS = url.startsWith("https://");

        // Definindo o RetryPolicy
        // Setting RetryPolicy
        this.setRetryPolicy(new DefaultRetryPolicy(REQUEST_TIMEOUT, NUM_REQUEST_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    @Override
    protected Response<SoapObject> parseNetworkResponse(NetworkResponse response) {
        try {
            // Coneverting the received String to SOAPObject
            // Convertendo String recebido em um objeto SOAP
            String soapString = new String(response.data, HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
            SoapObject soapResponseObject = _CreateSOAPObjectFromString(soapString);
            if ( soapResponseObject == null ) {
                // Error gerando objeto SOAP
                // Error generating SOAPObject
                return Response.error(new VolleyError("[NULL RESPONSE VOLLEY OBEJCT]: Error parsing XML response to SOAP Object"));
            }

            // Tudo OK, enviando sucesso
            // All Done, sending success
            return Response.success(soapResponseObject, HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (SoapFault soapFault) {
            return Response.error(new ParseError(soapFault));
        } catch (XmlPullParserException e) {
            return Response.error(new ParseError(e));
        } catch (IOException e) {
            return Response.error(new VolleyError("Error converting XmlPullParser to SoapObejct"));
        }
    }

    @Override
    protected void deliverResponse(SoapObject response) {
        if ( mListener != null )
            mListener.onResponse(response);
    }

    @Override
    public String getPostBodyContentType() {
        return getBodyContentType();
    }

    @Override
    public String getBodyContentType() {
        return PROTOCOL_CONTENT_TYPE;
    }

    @Override
    protected void onFinish() {
        super.onFinish();
        mListener = null;
    }

    @Override
    public byte[] getPostBody() {
        return getBody();
    }

    @Override
    public byte[] getBody() {
        // Cruindo o SOAPObject com o argumentos passados ´previamente
        // Making SOAPObject with the previously arguments
        final SoapObject requestObj = new SoapObject(soapNamespace, soapMethodName);
        for ( String key : rArguments.keySet() ) {
            final Object value = rArguments.get(key);
            final PropertyInfo soapProperty = new PropertyInfo();

            soapProperty.setName(key);
            soapProperty.setValue(value);
            soapProperty.setType(Objects.class);
            requestObj.addProperty(soapProperty);
        }

        // Geranto o SOAPTransportEnvelop
        // Generating SOAP transport envelop...
        Transport soapTransport = (this.isHTTPS) ? new HttpsTransportSE("", 80, "", 10) : new HttpTransportSE("");
        soapTransport.debug = true;
        SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        envelope.dotNet = true;
        envelope.setOutputSoapObject(requestObj);
        try {
            soapTransport.call(soapAction, envelope);
        } catch (Exception e) { }

        // Obtendo e retornando os bytes gerados do SOAP-Envelop
        // Getting and returning generated bytes from SOAP-Envelop
        try {
            if( showSoapEnvelop ) VolleyLog.d("SOAPEnvelop -> %s", soapTransport.requestDump);
            return soapTransport.requestDump.getBytes(PROTOCOL_CHARSET);
        } catch (UnsupportedEncodingException e) {
            VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of SoapArgumentos using %s",PROTOCOL_CHARSET);
            return null;
        }
    }

    /**
     * Metodo para converter a String recebida do servidor no objeto Soap (SoapObject)
     * Method to generate the SoapObject directly from received String by server
     * @param xmlString -> Input XML in String Format
     * @return SoapObject (KSoapLib)
     * @throws XmlPullParserException
     * @throws IOException
     */
    protected SoapObject _CreateSOAPObjectFromString(final String xmlString) throws XmlPullParserException, IOException {
        // Gerando um XMLPullParser com a resposta obtida...
        // Generating a XMLpullParser from server response...
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xmlString));

        /* @Info:
        Vendo o código do SoapSerializationEnvelope, vi que ele converte o
        XmlPullParser direto para um SoapObject, e vendo o XmlPullParser, vi que tem suporte de
        gerar este objeto (XmlPullParser) direto de uma String...

        Então, decidi que assim que eu receber a resposta do servidor, eu devo gerar o XmlPullParser
        para só depois, gerar o SoapObject.

        Esse foi o modo que encontrei, pois, não tem como gerar diretamente da String :/
        Mas não importa, pois desse modo, funciona muito bem também! :D
        --------------------------------------------------------------------------------------------
        Reading the source code from SoapSerializationEnvelope, I noticed that it is generated from
        XmlPullParser directly to a SoapObject, and reading the source code from XmlPullParser,
        I saw that it can support to generated that object(XMLPullParser) directly from a String.

        So, I decided that, as soon i get response from server, I'll generate a
        XmlPullParser to after generate a SoapObject from it.

        This is the way I found, because, the SoapObject not support to be generated directly
        from a String :/
        But, it doesn't mater, because, that way, it works very well too.
         */

        // PT -> Gerando o SoapObject com o XmlPullParser gerado previamente
        //    -> Generating SoapObject using the Xml generated by XmlPullparser previusly used
        SoapSerializationEnvelope env =  new SoapSerializationEnvelope(SoapEnvelope.VER11);
        env.dotNet = true;
        env.parse(parser);

        // Retornando o objeto gerado...
        // Retuning generated object
        SoapObject object = (SoapObject) env.bodyIn;
        return object;
    }

    /**
     * Este metodo define o status da visualização do Envelop SOAP dentro do log do Voley
     * This method set status to view the SOAPEnvelop inside Volley's log
     * @param showSoapEnvelop
     */
    public void setShowSoapEnvelop(boolean showSoapEnvelop) {
        this.showSoapEnvelop = showSoapEnvelop;
    }
}

