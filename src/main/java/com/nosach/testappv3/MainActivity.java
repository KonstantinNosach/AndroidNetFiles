package com.nosach.testappv3;

import android.content.Context;
import android.content.Intent;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private final static String ATTRIBUTE_NAME = "name";
    final static String ATTRIBUTE_STATE = "state";
    final static String ATTRIBUTE_PROGRESSBAR = "progessbar";


    private ListView listView;
    private SimpleAdapter simpleAdapter;


    private String fileServer = "http://blabla.com/";


    private List<Map<String, Object>> item;
    private List<String> urlList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String jsonFile = "backend.json";

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {
            String jsonString = readJSONFile(fileServer + File.separator + jsonFile);
            JSONObject jsonRootObject = new JSONObject(jsonString);
            //Get the instance of JSONArray that contains JSONObjects
            JSONArray jsonArray = jsonRootObject.optJSONArray("file_list");
            //List of items in ListView
            item = new ArrayList<>(jsonArray.length());
            //List of file's names with extensions
            urlList = new ArrayList<>(jsonArray.length());
            //Map of item's parts(state, file's title, progress bar)
            Map<String, Object> partOfItem;
            //Iterate the jsonArray and print the info of JSONObjects and initialising lists
            for (int i = 0; i < jsonArray.length(); i++) {
                partOfItem = new HashMap<>();

                JSONObject jsonObject = jsonArray.getJSONObject(i);
                urlList.add(jsonObject.optString("url"));

                partOfItem.put(ATTRIBUTE_NAME, jsonObject.optString("name"));
                partOfItem.put(ATTRIBUTE_STATE, "absent");
                partOfItem.put(ATTRIBUTE_PROGRESSBAR, 0);

                item.add(partOfItem);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] from = {ATTRIBUTE_NAME, ATTRIBUTE_PROGRESSBAR, ATTRIBUTE_STATE};
        int[] to = {R.id.tVName, R.id.pBLoad, R.id.tVState};

        simpleAdapter = new SimpleAdapter(this, item, R.layout.item, from, to);
        simpleAdapter.setViewBinder(new MyViewBinder());

        listView = (ListView) findViewById(R.id.listView);

        listView.setAdapter(simpleAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String stringState = (String) item.get(position).get(ATTRIBUTE_STATE);

                if (stringState.equals("absent") || stringState.equals("error")) {

                    DownloadTask downloadTask = new DownloadTask(item.get(position), urlList.get(position));
                    downloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, fileServer);


                } else if (stringState.equals("downloaded")) {
                    String localURL = "file:" + getFilesDir() + File.separator + urlList.get(position);
                    String extension = MimeTypeMap.getFileExtensionFromUrl(localURL);
                    String mType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(localURL), mType);

                    startActivity(Intent.createChooser(intent, "Choose an application to open with:"));
                }
            }
        });

        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            }
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.action_mode_context, menu);
                return true;
            }
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
                SparseBooleanArray sba = listView.getCheckedItemPositions();
                for (int i = 0; i < sba.size(); i++) {
                    int key = sba.keyAt(i);
                    if (sba.get(key)) {
                        String stringState = (String) item.get(key).get(ATTRIBUTE_STATE);
                        if (stringState.equals("absent") || stringState.equals("error")) {
                            DownloadTask downloadTask = new DownloadTask(item.get(key), urlList.get(key));
                            downloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, fileServer);
                        }
                    }
                }
                mode.finish();
                return false;
            }
            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    class MyViewBinder implements SimpleAdapter.ViewBinder {

        @Override
        public boolean setViewValue(View view, Object data, String textRepresentation) {
            int i;
            switch (view.getId()) {

                case R.id.pBLoad:
                    i = ((int) data);
                    ((ProgressBar) view).setProgress(i);
                    if (i > 0 & i < 100)
                        view.setVisibility(View.VISIBLE);
                    else view.setVisibility(View.INVISIBLE);

                    return true;
                default:
                    return false;
            }
        }
    }

    private static String readJSONFile(String urlString) throws Exception {
        BufferedReader reader = null;
        URL url = new URL(urlString);

        try {
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder buffer = new StringBuilder();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read);

            return buffer.toString();
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private String fileName;
        private Map map;

        public DownloadTask(Map<String, Object> map, String fileName) {

            this.fileName = fileName;
            this.map = map;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0] + fileName);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = openFileOutput(fileName, Context.MODE_WORLD_READABLE);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));

                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null) {
                        output.flush();
                        output.close();
                    }
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            map.put(ATTRIBUTE_STATE, "downloading");
            simpleAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);

            map.put(ATTRIBUTE_PROGRESSBAR, progress[0]);
           if(progress[0]%5==0)

            simpleAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                map.put(ATTRIBUTE_STATE, "error");
                simpleAdapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "Download error: " + result, Toast.LENGTH_LONG).show();
            } else {

                map.put(ATTRIBUTE_STATE, "downloaded");
                simpleAdapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "File downloaded", Toast.LENGTH_SHORT).show();
            }
        }
    }
}