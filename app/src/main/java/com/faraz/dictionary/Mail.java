package com.faraz.dictionary;

import java.util.Date;
import java.util.Properties;

import javax.activation.CommandMap;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.MailcapCommandMap;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class Mail extends javax.mail.Authenticator {
  private final String _port;
  private final String _sport;
  private final String _host;
  private final boolean _auth;
  private final boolean _debuggable;
  private final Multipart _multipart;
  private String _user;
  private String _pass;
  private String[] _to;
  private String _from;
  private String _subject;
  private String _body;

  public Mail() {
    _host = "smtp.gmail.com"; // default smtp server
    _port = "465"; // default smtp port
    _sport = "465"; // default socketfactory port

    _debuggable = false; // debug mode on or off - default off
    _auth = true; // smtp authentication - default on

    _multipart = new MimeMultipart();

    // There is something wrong with MailCap, javamail can not find a
    // handler for the multipart/mixed part, so this bit needs to be added.
    MailcapCommandMap mc = (MailcapCommandMap) CommandMap
        .getDefaultCommandMap();
    mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
    mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml");
    mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
    mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
    mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
    CommandMap.setDefaultCommandMap(mc);
  }

  public Mail(String user, String pass) {
    this();
    _user = user;
    _pass = pass;
  }

  public boolean send() throws Exception {
    Properties props = _setProperties();

    if (!_user.equals("") && !_pass.equals("") && _to.length > 0
        && !_from.equals("") && !_subject.equals("")
        && !_body.equals("")) {
      Session session = Session.getInstance(props, this);

      MimeMessage msg = new MimeMessage(session);

      msg.setFrom(new InternetAddress(_from, "Personal Dictionary"));

      InternetAddress[] addressTo = new InternetAddress[_to.length];
      for (int i = 0; i < _to.length; i++) {
        addressTo[i] = new InternetAddress(_to[i]);
      }
      msg.setRecipients(MimeMessage.RecipientType.TO, addressTo);
      msg.setSubject(_subject);
      msg.setSentDate(new Date());
      msg.setHeader("Content-Type", "text/html");
      msg.setHeader("X-Priority", "1");
      Multipart mp = new MimeMultipart();
      MimeBodyPart htmlPart = new MimeBodyPart();
      htmlPart.setContent(_body, "text/html");
      mp.addBodyPart(htmlPart);
      msg.setContent(mp, "text/html");
      // send email
      Transport.send(msg);
      return true;
    }
    else {
      return false;
    }
  }

  public void addAttachment(String filename) throws Exception {
    BodyPart messageBodyPart = new MimeBodyPart();
    DataSource source = new FileDataSource(filename);
    messageBodyPart.setDataHandler(new DataHandler(source));
    messageBodyPart.setFileName(filename);

    _multipart.addBodyPart(messageBodyPart);
  }

  @Override
  public PasswordAuthentication getPasswordAuthentication() {
    return new PasswordAuthentication(_user, _pass);
  }

  private Properties _setProperties() {
    Properties props = new Properties();

    props.put("mail.smtp.host", _host);

    if (_debuggable) {
      props.put("mail.debug", "true");
    }

    if (_auth) {
      props.put("mail.smtp.auth", "true");
    }

    props.put("mail.smtp.port", _port);
    props.put("mail.smtp.socketFactory.port", _sport);
    props.put("mail.smtp.socketFactory.class",
        "javax.net.ssl.SSLSocketFactory");
    props.put("mail.smtp.socketFactory.fallback", "false");

    return props;
  }

  public void setBody(String _body) {
    this._body = _body;
  }

  public void set_to(String[] _to) {
    this._to = _to;
  }

  public void set_from(String _from) {
    this._from = _from;
  }

  public void set_subject(String _subject) {
    this._subject = _subject;
  }
}
