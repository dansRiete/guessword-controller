## Deployment instructions
Build docker image
<pre>
sudo docker build -t alexsoft/guessword ~/IdeaProjects/guessword-controller && \
sudo docker tag alexsoft/guessword alexkzk/guessword:1.03 && \
sudo docker push alexkzk/guessword:1.03
</pre>
Restart container
<pre>
sudo docker stop guessword && \
sudo docker rm guessword && \
sudo docker run --restart=always -d -p 8085:8080 -p 5005:5005 --name=guessword alexkzk/guessword:1.04 && \
sudo docker logs -f guessword
</pre>
