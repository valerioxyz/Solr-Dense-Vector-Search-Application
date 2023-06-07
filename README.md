# Dense Vector Search Application: Query By Example

# PDF Tesina contenuto nella cartella "tesina"

# 1. Scrivere CustomRequestHandler (o altre classi)

```java
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;


public class CustomRequestHandler extends RequestHandlerBase {

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        rsp.add("message", "Custom request handler is working!");
    }

    @Override
    public String getDescription() {
        return "Custom Request Handler";
    }
    @Override
    public Name getPermissionName(AuthorizationContext context) {
        // Return the permission name based on the authorization context
        return null;
    }
}
```
# 2. Compilare in java
```cmd
javac -classpath solr-core-9.2.1.jar;solr-solrj-9.2.1.jar;solr-solrj-streaming-9.2.1.jar;solr-solrj-zookeeper-9.2.1.jar CustomRequestHandler.java
```

```cmd
jar cvf custom-handler.jar CustomRequestHandler.class
```

O alternativamente in Maven

```cmd
mvn clean install
```

Maven è utile per gestire le dipendenze del progetto.

# 3. Configurare Solr

Andare in Core Admin, creare core copiando nella cartella i file da _default andando a specificare configurazioni particolari di `managed-schema.xml` e `solrconfig.xml`. Nel nostro caso un `RequestHandler`

Da adesso in poi, ci basta cambiare il file `solrconfig.xml` e riavviare solr per effettuare dei cambiamenti.

Nella parte adibita alle lib, si specifica la libreria da caricare (plugin)
```xml
<lib dir="${solr.install.dir:../../../..}/lib" regex="CustomHandler-1\.jar" />
```
Inoltre, si specifica che si utilizzerà la classe come `RequestHandler`, nel file del configset `solrconfig.xml`:

```xml
<requestHandler name="/custom" class="me.val.plugins.CustomRequestHandler" />
```

# 4. Caricare plugin su solr 
Il jar dovrà essere caricato nella cartella `.\solr\lib`.

Il caricamento del plugin è confermato dalla pagina: http://localhost:8983/solr/#/new_core123/plugins?type=query

Sono stati aggiunti nella directory `solr-9.2.1\lib`, insieme al plugin `CustomHandler-1.jar` le librerie per tensorflow:
* `libtensorflow-1.15.0-javadoc.jar`
* `libtensorflow-1.15.0-sources.jar`
* `libtensorflow-1.15.0.jar`
* `libtensorflow_jni-1.15.0.jar`

è stato disabilitato il Security Manager perché non consentiva di modificare il classpath

```cmd
IF NOT DEFINED SOLR_SECURITY_MANAGER_ENABLED (
  set SOLR_SECURITY_MANAGER_ENABLED=false
)
```

# 5. Import saved_model to Apache Solr plugin

Il jar contiene `resources\saved_model.zip` che è uno zip contenente la snapshot della rete neurale opportunamente tunata. Viene unzippato all'avvio e messo nella cartella temporanea di solr: `.\solr\tmp`.

# 6. Preprocessing 

1. Ridimensionamento: se l'immagine è di dimensione diversa da 224x224, viene portata a tale risoluzione
2. La funzione img_to_array converte un'immagine di tipo PIL (Python Imaging Library) in un array NumPy. In Java, la funzione `normalizeImage_f` prende in input un array multidimensionale `image` di tipo `float[][][][]`, che rappresenta un'immagine con dimensioni di batch, altezza, larghezza e canali. La funzione itera su tutti i pixel dell'immagine e applica la normalizzazione seguendo i passaggi seguenti:
    In poche parole, la funzione normalizza l'immagine sottraendo il valore medio e dividendo per il fattore di scala, mentre inverte i canali rosso e blu.

con `saved_model_cli` è stato visto i nomi dei tensori di input e di output della rete.


# 7. Aggiungere dense vector a `managed-schema.xml`

```xml
<fieldType name="knn_vector" class="solr.DenseVectorField" vectorDimension="64" similarityFunction="cosine"/>

<field name="feature_vector" type="knn_vector" indexed="true" stored="true" required="true"/>
<field name="image_id" type="string" indexed="false" required="true" />
<field name="image_path" type="string" indexed="false" required="true" />
<field name="scientific_name" type="text_general" indexed="true"/>
<field name="common_name" type="text_general" indexed="true" />
<field name="description" type="text_general" indexed="true" />

<uniqueKey>image_id</uniqueKey>
```

in `managed-schema.xml`

e

```xml
<config>
<codecFactory class="solr.SchemaCodecFactory"/>
```
in `solrconfig.xml`

# 8. Aggiunta documenti tramite interfaccia documents
Tramite l'endpoint `/update` è stato caricato il json contenente tutti i documenti (compresi i vettori di feature).

comando per eliminare tutti i documenti: 

```cmd
curl -X POST -H "Content-Type: application/json" -d "{'delete': {'query': '*:*'}}" "http://localhost:8983/solr/{collection_name}/update?commit=true"
```
# 9. Effettuare query di prova su `/select`

Usando `q={!knn f=feature_vector topK=10}[x_1, x_2, ..., x_64]` restituisce i più simili. Per capire quanto sono simili, ricalcoliamo nel plugin la cosine_similarity.

# 10. Interagire col plugin attraverso l'entry point `/custom`

```cmd
curl -X POST -H "Content-Type: image/png" --data-binary "@0070039.png" http://localhost:8983/solr/new_core123/custom
```

# 11. Richieste dall'esterno: Consentire CORS

CORS (Cross-Origin Resource Sharing) è una politica di sicurezza implementata nei browser web per proteggere gli utenti da possibili attacchi di tipo cross-origin. In termini semplici, CORS controlla se un'applicazione web su un dominio (origine) è autorizzata a fare richieste a un altro dominio diverso.

Quando un browser esegue una richiesta AJAX da un'origine (dominio, protocollo e porta) a un'altra, viene eseguita una verifica CORS. Se l'origine della richiesta non è autorizzata dal server di destinazione, il browser bloccherà la richiesta e genererà un errore CORS.

Abilitare CORS è utile per contattare Solr da `localhost` attraverso un file `index.html`.

```xml
<filter>
<filter-name>cross-origin</filter-name>
<filter-class>org.eclipse.jetty.servlets.CrossOriginFilter</filter-class>
<init-param>
    <param-name>allowedOrigins</param-name>
    <param-value>*</param-value>
</init-param>
<init-param>
    <param-name>allowedMethods</param-name>
    <param-value>GET,POST,OPTIONS,DELETE,PUT,HEAD</param-value>
</init-param>
<init-param>
    <param-name>allowedHeaders</param-name>
    <param-value>origin, content-type, accept</param-value>
</init-param>
</filter>

<filter-mapping>
<filter-name>cross-origin</filter-name>
<url-pattern>/*</url-pattern>
</filter-mapping>
```

Filtro aggiunto in `.\solr-9.2.1\server\solr-webapp\webapp\WEB-INF\web.xml`
Il file `web.xml` in Apache Solr è un file di configurazione che definisce il comportamento del servlet container (ad esempio, Apache Tomcat) per il deployment di Solr come applicazione web.
