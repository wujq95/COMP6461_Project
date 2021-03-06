package server;

import java.io.*;
import java.util.Arrays;
import java.util.List;


public class ParseRequest {

    private boolean deBugging;
    private String fileDirectory;

    public ParseRequest(boolean deBugging, String fileDirectory){
        this.deBugging = deBugging;
        this.fileDirectory = fileDirectory;
    }

    /**
     * parse request
     * @param payload
     * @return
     */
    public Request parseRequest(String payload){
        Request request = new Request();
        List<String> strings = Arrays.asList(payload.split("\r\n"));
        String str1 = strings.get(0);
        String[] str = str1.split(" ");
        request.setMethod(str[0]);
        request.setPath(str[1]);
        request.setVersion(str[2]);

        int i = 1;
        while(i<strings.size()&&!strings.get(i).equals("")){
            request.addHeaders(strings.get(i));
            i++;
        }

        if(request.getMethod().toLowerCase().equals("post")){
            String data = "";
            while(i<strings.size()){
                data += strings.get(i++);
            }
            request.setData(data);
        }
        return request;
    }

    /**
     * create response
     * @param request
     * @return
     * @throws IOException
     */
    public synchronized Response createResponse(Request request) throws IOException {
        Response response = new Response();
        String path = request.getPath();

        if(!checkPath(path)){
            response.setCode(400);
            response.setPhrase("Bad Request");
            return response;
        }

        if(request.getMethod().toLowerCase().equals("get")){
            if(path.endsWith("/")){
                response.setCode(200);
                response.setPhrase("OK");
                response.setHeaders("content-type: text/plain");
                response.setHeaders("Content-Disposition: inline");
                File dir = new File(fileDirectory);
                File[] list = dir.listFiles();
                for(File f: list){
                    if(f.isFile()){
                        response.setData(f.getName());
                    }
                }
            }else {
                File file = new File(fileDirectory + path);
                if(file.exists()&&file.isFile()){
                    String headContent = "Content-type: text/plain";
                    if(path.endsWith(".html")){
                        headContent = "Content-type: text/html";
                    }else if(path.endsWith(".xml")){
                        headContent = "Content-type: text/xml";
                    }else if(path.endsWith(".json")){
                        headContent = "Content-type: application/json";
                    }else if(path.endsWith(".pdf")){
                        headContent = "Content-type: application/pdf";
                    }
                    response.setHeaders(headContent);
                    response.setHeaders("Content-length: "+ file.length());
                    response.setHeaders("Content-Disposition:attachment;filename="+path.substring(1));

                    response.setCode(200);
                    response.setPhrase("OK");
                    readFile(response,file);
                }else{
                    response.setCode(404);
                    response.setPhrase("Not Found");
                }
            }
        }else if(request.getMethod().toLowerCase().equals("post")){
            if(path.endsWith("/")) {
                response.setCode(400);
                response.setPhrase("Bad Request");
            }else{
                File file = new File(fileDirectory + path);
                if(!file.exists()){
                    file.createNewFile();
                }
                response.setCode(200);
                response.setPhrase("OK");
                String data = request.getData();
                FileWriter fw;
                try{
                    fw = new FileWriter(fileDirectory + path);
                    fw.write(data);
                    fw.flush();
                    fw.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
                response.setData("Manage file successfully!");
            }
        }else{
            response.setCode(400);
            response.setPhrase("Bad Request");
        }
        return response;
    }


    /**
     * handle the response to String payload
     * @param response
     * @return
     */
    public String handleResponse(Response response){
        String payload = "";
        payload += "HTTP/1.0 " + response.getCode() +" " + response.getPhrase() + "\r\n";

        if(response.getCode()==200) {
            for(String str: response.getHeaders()){
                payload += str + "\r\n";
            }
            payload += "\r\n";
            payload += response.getData() + "\r\n";
        }

        return payload;
    }

    /**
     * read file
     * @param response
     * @param f
     * @throws IOException
     */
    public void readFile(Response response, File f) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
        String str = br.readLine();
        while(str!=null){
            response.setData(str);
            str = br.readLine();
        }
        br.close();
    }


    /**
     * check path format
     * @param path
     * @return
     */
    public boolean checkPath(String path){
        if(path.length()==0||(!path.startsWith("/"))) return false;
        char[] chars = path.toCharArray();
        for(int i=1;i<chars.length;i++){
            if(chars[i]=='/') return false;
        }
        return true;
    }
}
