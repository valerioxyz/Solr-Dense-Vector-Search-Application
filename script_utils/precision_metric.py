import json
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity

feature_vectors = '10_epochs_vector64_1685547139.json'
input_img = 'images\\0050020.png'

with open(feature_vectors, 'r') as file:
    docs = json.load(file)


def retrieve_top_k_similar(doc, docs, topK=10):

    similarities = {}
    for k,v in docs.items():
        # Convert the vector from the file to a NumPy array
        vector = np.array(v['feature_vector'])


        # Reshape the vectors to be 2D arrays
        vector1_2d = np.array(doc['feature_vector']).reshape(1, -1)
        vector2_2d = vector.reshape(1, -1)

        # Calculate cosine similarity
        similarity = cosine_similarity(vector1_2d, vector2_2d)
        similarities[k]=(similarity[0][0])
    similarities = dict(sorted(similarities.items(), key=lambda x: x[1],reverse=True))
    return list(similarities)[:topK]

def extract_prefix(str):
    return str[:10]

dict_docs = {}

for doc in docs:
    dict_docs[doc['image_path']]=doc

topK = 10
precision = []

for k,v in dict_docs.items():
    temp_precision = 0
    similar_docs = (retrieve_top_k_similar(v,dict_docs,topK))

    relevant_label = extract_prefix(k)

    for doc in similar_docs:
        if relevant_label == (extract_prefix(doc)):
            temp_precision +=1
    
    precision.append(temp_precision/topK)
    temp_precision=0
    #print(precision)
    print(k,"done")

precision = np.array(precision)

print(np.mean(precision))