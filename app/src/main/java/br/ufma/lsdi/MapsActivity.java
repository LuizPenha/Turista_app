package br.ufma.lsdi;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

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
import br.ufma.lsdi.databinding.ActivityMapsBinding;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    CDDL cddl; //instancia do CDDL

    private ConnectionImpl con_int;
    private ConnectionImpl con_ext;
  /*   private Double latg = 0.0;
    private Double lngg = 0.0;
    private Double latt = 0.0;
    private Double lngt = 0.0;  */
    private EPServiceProvider ep;
    // private Double val = 0.0;
    // private Double v2 = 0.0;
    private TextView messageTextView;
    private View sendButton;

    private GoogleMap mMap, mMap2;
    private ActivityMapsBinding binding;

    private Double latt = -2.48754;
    private Double lngt = -44.29282;
    private Double latg = -2.48832;
    private Double lngg = -44.29010;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        //    appGuia();
        appTur();
        configCEP();

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
      //  LatLng tur = new LatLng (getLatt(), getLngt());
        LatLng tur = new LatLng (latt, lngt);
        mMap.addMarker(new MarkerOptions().position(tur).title("Turista"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(tur));
     //   mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(tur, 14));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(14));
     //   mMap.clear();

  //      mMap2 = googleMap;
        LatLng guia = new LatLng (latg, lngg);
        mMap.addMarker(new MarkerOptions().position(guia).title("Guia"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(guia));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(guia, 14));
    }


    // O GUIA RECUPERA SUA LOCALIZAÇÃO A PARTIR DO DISPOSITIVO, PUBLICA NUM BROKER EXTERNO - HiveMQ - e
    // EVENTUALMENTE RECEBE UMA MSG DO TURISTA
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
        cddl.setContext(this); //activity atual iniciando a CDDL
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
                    enviarlocBrokerext(latg,lngg); //invoca o método de publicação no broker externo
                    //passando as coordenadas do guia
                    Log.d("_MAIN", "-------- GEOLOCALIZAÇÃO DO GUIA --------  " + latg + "  " + lngg);
                }
            }
        });

        // Recebe msg de alerta do turista
        Subscriber sub2 = SubscriberFactory.createSubscriber();
        sub2.addConnection(con_ext);
        sub2.subscribeServiceByName("msgalertatur");  //subscreve msg de alerta do Guia
        sub2.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                if (message.getServiceName().equals("msgalertatur")) {
                    //        Log.d("_MAIN", "******* TURISTA FORA DO BANDO:  *******" + message.getPublisherID());
                    Double lat_tur = 0.0;
                    Double lng_tur = 0.0;
                    String loc_tur = message.getServiceByteArray().toString();
                    String [] coord_tur = loc_tur.split("_"); //quebra a string no _
                    lat_tur = Double.parseDouble(coord_tur[0]);
                    lng_tur = Double.parseDouble(coord_tur[1]);
                    Log.d("_MAIN", "******* TURISTA FORA DO BANDO:  *******" + message.getPublisherID());
                    Log.d("_MAIN", "COORDENADAS DO TURISTA " + message.getPublisherID() +": " + lat_tur + ", " + lng_tur);
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


    ///////////////////////////////////////////////////////////

    // TURISTA RECUPERA SUA LOCALIZAÇÃO A PARTIR DO DISPOSITIVO,
    // VERIFICA SE SUAS COORDENADAS ESTÃO DENTRO DA ÁREA DO DISCO DE RAIO r ATRAVÉS DE PROCESSAMENTO CEP
    // EVENTUALMENTE ENVIA UMA MSG PARA O GUIA
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
        cddl.setContext(this);
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
                    mMap.clear();

                    Log.d("_MAIN", " ------- GEOLOCALIZAÇÃO DO TURISTA -------  " + latt + "  " + lngt);
                }
            }
        });

        //turista recebe os dados de geolocalização do Guia
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

                    //implementar o marker com a camera

                    //MapsActivity teste = new MapsActivity(latt, lngt, latg, lngg);
                    Log.d("_MAIN", "Dados de geolocalizacao do guia recebidos pelo turista: " + loc_guia);
                }
            }
        });
    }

    //Msg de alerta do turista para o Guia caso esteja fora do bando
    private void enviaralertaGuia(double lat, double lng) {
        Publisher publisher = PublisherFactory.createPublisher();
        publisher.addConnection(con_ext);
        MyMessage message = new MyMessage();
        message.setServiceName("msgalertatur");
        message.setServiceByteArray(lat + "_" + lng);
        publisher.publish(message);
    }

    private void configCEP(){
        ep = EPServiceProviderManager.getDefaultProvider();
        ep.getEPAdministrator().getConfiguration().addEventType(Evento.class);
        String pos =  "INSERT INTO ForaBando" +
                " SELECT br.ufma.lsdi.Distancia.dist(latt, lngt, latg, lngg) as valdist FROM Evento";
        EPStatement cepStatement = ep.getEPAdministrator().createEPL(pos);
        String fbando =  "SELECT * FROM ForaBando" +
                " WHERE valdist > 0.1"; //distancia > raio do disco 100 m
        EPStatement cepForabando = ep.getEPAdministrator().createEPL(fbando);
        cepForabando.addListener(new UpdateListener() {
            @Override
            public void update(EventBean[] newData, EventBean[] oldData) {
                enviaralertaGuia(latt, lngt); //Guia recebe msg de alerta do turista com as coord
                Log.d(" >>>>>>> Distância do turista fora do bando:  ", newData[0].get("valdist").toString());
            }
        });
    }
    @Override
    protected void onDestroy() {
        cddl.stopLocationSensor();
        cddl.stopAllCommunicationTechnologies();
        cddl.stopService(); //parando o serviço CDDL
        con_int.disconnect(); //desconectando do microbroker CDDL
        con_ext.disconnect(); //desconectadno do broker HiveMQ
        CDDL.stopMicroBroker();
        super.onDestroy();
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

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            //publishMessage();
        }
    };



    private boolean checkPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int result = ContextCompat.checkSelfPermission(getApplicationContext(), READ_EXTERNAL_STORAGE);
            int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
//                intent.putExtra(caFileName, caFileName);
//                intent.putExtra(certFileName, certFileName);
//                intent.putExtra(caAlias, caAlias);
                //  startActivityForResult(intent, 2296);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                //  startActivityForResult(intent, 2296);
            }
        } else {
            //below android 11
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE}, 1);
        }
    }

    private void setCertificates(String caFileName, String certFileName, String caAlias) {
        if (!checkPermission()) {
            requestPermission();
        } else {
            try {
                SecurityService securityService = new SecurityService(getApplicationContext());
                securityService.setCaCertificate(caFileName, caAlias);
                securityService.setCertificate(certFileName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2296) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "All permissions are granted, you may import certificates!", Toast.LENGTH_SHORT).show();
//                    try{
//                        SecurityService securityService = new SecurityService(getApplicationContext());
//                        String caFileName = data.getStringExtra("caFileName");
//                        String certFileName = data.getStringExtra("certFileName");
//                        String caAlias = data.getStringExtra("caAlias");
//
//                        securityService.setCaCertificate(caFileName, caAlias);
//                        securityService.setCertificate(certFileName);
//                    }
//                    catch (Exception e){
//                        e.printStackTrace();
//                    }
                } else {
                    Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}

