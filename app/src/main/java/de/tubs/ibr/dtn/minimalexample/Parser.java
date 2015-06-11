package de.tubs.ibr.dtn.minimalexample;

/**
 * Created by Sibren on 6/4/2015.
 */
public class Parser {
    private String domain;
    private String version;
    private int port;
    private String protocol;
    private String method;
    private String resource;
    private String request;

    public Parser(){
        domain="";
        version="";
        port=80;
        protocol="";
        method="";
    }

    public class BadRequestException extends Exception{
        public BadRequestException(String text){
            super(text);
        }
    }



    public Parser(String requestLine) throws BadRequestException{

        String[] parts=null;

        try{
            parts=requestLine.split(" ");
            method=parts[0];
            version=parts[2];
        } catch (NullPointerException e){
            throw new BadRequestException("Null request line");
        } catch (ArrayIndexOutOfBoundsException e){
            throw new BadRequestException("Missing method, version or resource");
        }

        String[] slashes=parts[1].split("/");
        protocol=slashes[0];
        if(protocol.endsWith(":")){
            protocol=protocol.substring(0,protocol.length()-1);
        }
        try{
            domain=slashes[2];
        } catch (ArrayIndexOutOfBoundsException e){
            throw new BadRequestException("Badly formated resource");
        }
        int colonLoc=domain.indexOf(':');
        if(colonLoc==-1){
            port=80;
        } else {
            try {
                port = Integer.parseInt(domain.substring(colonLoc+1));
            } catch (NumberFormatException e){
                throw new BadRequestException("Bad port number");
            }
            if(port > 65535){
                throw new BadRequestException("Port number too big");
            } else if (port < -1){
                throw new BadRequestException("Port number too small");
            }
            domain = domain.substring(0,colonLoc);
        }
        resource="";
        for(int i=3; i < slashes.length; i++){
            resource+="/"+slashes[i];
        }
        if(parts[1].endsWith("/")){
            resource+="/";
        }

        request=method + " " + resource + " " + version + "\r\n";
        request+="Host: " + domain + "\r\n";
        request+="Connection: close\r\n";

    }

    public void addHeader(String headerLine){
        String[] parts=headerLine.split(":");
        if(parts[0].equals("Host") || parts[0].equals("Connection")){
            return;
        }
        request+=headerLine+"\r\n";
        return;
    }

    public String getURL(){ return protocol+"://"+domain+resource;}

    public String getResource(){ return resource; }

    public String toString(){
        return request;
    }

    public String getDomain(){
        return domain;
    }

    public int getPort(){
        return port;
    }

    public String getVersion(){
        return version;
    }

    public String getProtocol(){
        return protocol;
    }

    public String getMethod(){
        return method;
    }
}
