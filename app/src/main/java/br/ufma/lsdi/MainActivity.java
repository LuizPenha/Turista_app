package br.ufma.lsdi;


import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.ConnectionFactory;
import br.ufma.lsdi.cddl.listeners.IConnectionListener;
import br.ufma.lsdi.cddl.listeners.ISubscriberListener;
import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.cddl.network.ConnectionImpl;
import br.ufma.lsdi.cddl.network.SecurityService;
import br.ufma.lsdi.cddl.pubsub.Publisher;
import br.ufma.lsdi.cddl.pubsub.PublisherFactory;
import br.ufma.lsdi.cddl.pubsub.Subscriber;
import br.ufma.lsdi.cddl.pubsub.SubscriberFactory;
//import br.ufma.lsdi.util;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private TextView messageTextView;
    private View sendButton;


    CDDL cddl; //instancia do CDDL
    private ConnectionImpl con_int;
    private ConnectionImpl con_ext;

    private Double latt = -2.48754;
    private Double lngt = -44.29282;
    private Double latg = -2.48832;
    private Double lngg = -44.29010;

    private Double lat_tur_alerta = 0.0;
    private Double lng_tur_alerta = 0.0;

    private GoogleMap mMap;
    private EPServiceProvider ep;

    private Marker markerTurista;
    private Marker markerGuia;
    private Marker markerAlerta;

    private LatLng latLngTemporaria;
    private LatLng latLngTemporaria2;
    private LatLng latLngTemporariaAlerta;
    private String modo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        //modo="GUIA";
        modo=" ";
        mapFragment.getMapAsync(this);

        setViews();
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        if(modo.equals("GUIA")){
            appGuia();
        }else{
            appTur();
            configCEP();
        }
    }

    private void appGuia(){
        //estabelece a conexao interna com o microbroker
        String host = CDDL.startMicroBroker(); //broker MQTT rodando no dispositivo
        con_int = ConnectionFactory.createConnection();
        con_int.setClientId("guiatur");
        con_int.setHost(host); //passa o microbroker para conexão
        con_int.addConnectionListener(connectionListener); //recebe o status da conexão
        con_int.connect(); //conecta com o microbroker

        //estabelece a conexao com o broker externo HiveMQ
        con_ext = ConnectionFactory.createConnection();
        con_ext.setClientId("guiatur");
        con_ext.setHost("broker.hivemq.com"); //seta o HiveMQ para conexão
        con_ext.connect(); //conecta com o HiveMQ

        //inicia o CDDL e captura sua localização a partir do dispositivo
        cddl = CDDL.getInstance(); //obtem uma instancia do CDDL
        cddl.setConnection(con_int); //estabelece a conexão com o microbroker MQTT
        cddl.setContext(getApplicationContext()); //activity atual iniciando a CDDL
        cddl.startService(); //inicia o serviço
        cddl.startLocationSensor(); //captura a localização do dispositivo por meio do sensor
        cddl.startCommunicationTechnology(CDDL.INTERNAL_TECHNOLOGY_ID); //inicia a tecnologia de
        //comunicação de sensores internos

        //recebe os dados de sua localização através do microbroker
        Subscriber sub = SubscriberFactory.createSubscriber();
        sub.addConnection(cddl.getConnection());
        sub.subscribeServiceByName("Location"); //subscreve o serviço Location
        sub.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("Location")) {
                    latg = message.getSourceLocationLatitude();
                    lngg = message.getSourceLocationLongitude();
                    enviarlocBrokerext(latg,lngg); //publica as coordenadas do guia no broker externo

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            latLngTemporaria = new LatLng (latg, lngg);
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLngTemporaria));
                            markerGuia.setPosition(latLngTemporaria);
                        }
                    });
                    Log.d("_MAIN", "-------- GEOLOCALIZAÇÃO DO GUIA --------  " + latg + "  " + lngg);
                }
            }
        });

        // Recebe msg de alerta do turista
        Subscriber sub2 = SubscriberFactory.createSubscriber();
        sub2.addConnection(con_ext);
        sub2.subscribeServiceByName("msgalertatur");  //subscreve msg de alerta do turista
        sub2.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("msgalertatur")) {
                    String loc_tur = message.getServiceByteArray().toString();
                    String [] coord_tur = loc_tur.split("_"); //quebra a string no _
                    lat_tur_alerta = Double.parseDouble(coord_tur[0]);
                    lng_tur_alerta = Double.parseDouble(coord_tur[1]);
                    // Alerta
                    runOnUiThread(new Runnable() { //executa a ação na thread da UI
                        @Override
                        public void run() {
                            latLngTemporariaAlerta = new LatLng (lat_tur_alerta, lng_tur_alerta);
                            markerAlerta.setPosition(latLngTemporariaAlerta); //leva o marcador p/ posição atual
                        }
                    });
                    Log.d("_MAIN", "TURISTA " + message.getPublisherID() +" FORA DO BANDO: " + lat_tur_alerta + ", " + lng_tur_alerta);
                }
            }
        });
    }

    //recebe e envia os dados de localização do guia para publicação no HiveMQ
    private void enviarlocBrokerext(double latguia, double lngguia) {
        Publisher publisher = PublisherFactory.createPublisher();
        publisher.addConnection(con_ext); //adiciona a conexão externa
        MyMessage message = new MyMessage(); //instancia um objeto 'mensagem'
        message.setServiceName("bando"); //estabelece um serviço chamado 'bando' para
        // publicação das coord. do Guia no HiveMQ
        message.setServiceByteArray(latguia + "_" + lngguia); //passa as coord. como um ByteArray
        publisher.publish(message); //publica msg com as coord. do Guia no HiveMQ
    }

    private void configCEP(){
        ep = EPServiceProviderManager.getDefaultProvider();
        ep.getEPAdministrator().getConfiguration().addEventType(Evento.class);
        String pos =  "INSERT INTO ForaBando" +
                " SELECT br.ufma.lsdi.Distancia.dist(latt, lngt, latg, lngg) as valdist FROM Evento";
        EPStatement cepStatement = ep.getEPAdministrator().createEPL(pos);
        String fbando =  "SELECT * FROM ForaBando" +
                " WHERE valdist > 0.5"; //distancia > raio do disco 500 m
        EPStatement cepForabando = ep.getEPAdministrator().createEPL(fbando);
        cepForabando.addListener(new UpdateListener() {
            @Override
            public void update(EventBean[] newData, EventBean[] oldData) {
                enviaralertaGuia(latt, lngt); //Guia recebe msg de alerta do turista com as coord
                Log.d(" >>>>>>> Distância do turista fora do bando:  ", newData[0].get("valdist").toString());
            }
        });
    }

    private void enviaralertaGuia(double lat, double lng) {
        Publisher publisher = PublisherFactory.createPublisher();
        publisher.addConnection(con_ext);
        MyMessage message = new MyMessage();
        message.setServiceName("msgalertatur");
        message.setServiceByteArray(lat + "_" + lng);
        publisher.publish(message);
    }

    private void appTur(){
        //estabelece a conexao interna com microbroker)
        String host = CDDL.startMicroBroker();
        con_int = ConnectionFactory.createConnection();
        con_int.setClientId("turista1");
        con_int.setHost(host);
        con_int.addConnectionListener(connectionListener);
        con_int.connect();

        //inicia a conexão externa com o HiveMQ para subscrever dados do Guia
        con_ext = ConnectionFactory.createConnection();
        con_ext.setClientId("turista1");
        con_ext.setHost("broker.hivemq.com");
        con_ext.connect();

        //inicia o CDDL e captura sua localização
        cddl = CDDL.getInstance();
        cddl.setConnection(con_int);
        cddl.setContext(getApplicationContext());
        cddl.startService();
        cddl.startLocationSensor(); //capturar a localização do dispositivo
        cddl.startCommunicationTechnology(CDDL.INTERNAL_TECHNOLOGY_ID);

        //recebe seus dados de localização a partir do dispositivo
        Subscriber sub = SubscriberFactory.createSubscriber();
        sub.addConnection(cddl.getConnection());
        sub.subscribeServiceByName("Location");
        sub.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("Location")) {
                    latt = message.getSourceLocationLatitude();
                    lngt = message.getSourceLocationLongitude();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            latLngTemporaria = new LatLng (latt, lngt);
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngTemporaria, 15));
                            markerTurista.setPosition(latLngTemporaria);
                        }
                    });
                    Log.d("_MAIN", " ------- GEOLOCALIZAÇÃO DO TURISTA -------  " + latt + "  " + lngt);
                }
            }
        });

//        //turista recebe os dados de geolocalização do Guia
        Subscriber sub2 = SubscriberFactory.createSubscriber();
        sub2.addConnection(con_ext);
        sub2.subscribeServiceByName("bando"); //subscreve o serviço 'bando' do Guia no broker externo para receber as coord.
        sub2.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("bando")) {
                    String loc_guia = message.getServiceByteArray().toString();
                    String [] coordguia = loc_guia.split("_"); //quebra a string no _
                    latg = Double.parseDouble(coordguia[0]);
                    lngg = Double.parseDouble(coordguia[1]);
                    //verificar quebra na primeira execução
                    ep.getEPRuntime().sendEvent(new Evento(latt, lngt, latg, lngg));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            latLngTemporaria2 = new LatLng (latg, lngg);
                            markerGuia.setPosition(latLngTemporaria2);
                        }
                    });
                    Log.d("_MAIN", "Dados de geolocalizacao do guia recebidos pelo turista: " + loc_guia);
                }
            }
        });
    }

    private IConnectionListener connectionListener = new IConnectionListener() {
        @Override
        public void onConnectionEstablished() {
            messageTextView.setText("Conexão estabelecida.");
            Log.d("app bandoTur","Conexão estabelecida.");
        }

        @Override
        public void onConnectionEstablishmentFailed() {
            messageTextView.setText("Falha na conexão.");
        }

        @Override
        public void onConnectionLost() {
            messageTextView.setText("Conexão perdida.");
        }

        @Override
        public void onDisconnectedNormally() {
            messageTextView.setText("Uma desconexão normal ocorreu.");
        }

    };

    private void setViews() {
        sendButton = findViewById(R.id.sendButton);
        messageTextView = findViewById(R.id.messageTexView);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        if(modo.equals("GUIA")){
            mMap = googleMap;
      //      LatLng sydney = new LatLng(-34, 151);
            LatLng lit = new LatLng(-2.48832, -44.29010);
            markerGuia    = mMap.addMarker(new MarkerOptions().position(lit).title("GUIA"));
            markerAlerta    = mMap.addMarker(new MarkerOptions().position(lit).title("Alerta Tur").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(14));
        }else{
            mMap = googleMap;
    //        LatLng sydney = new LatLng(-34, 151); // MUDAR PARA Litoranea
            LatLng lit = new LatLng(-2.48832, -44.29010);
            markerTurista = mMap.addMarker(new MarkerOptions().position(lit).title("TURISTA"));
            markerGuia    = mMap.addMarker(new MarkerOptions().position(lit).title("GUIA"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(lit));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(14));
        }
    }
}