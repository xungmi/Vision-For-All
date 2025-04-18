FROM nvidia/cuda:11.4.2-cudnn8-devel-ubuntu20.04

RUN apt-get update && apt-get install -y \
python3-pip \
python3-dev \
cuda-toolkit-11-4 \
cudnn8-cuda-11-4 \
git

RUN pip3 install –upgrade pip

RUN pip3 install tensorflow-gpu==2.6.0

CMD [“python3”, “-c”, “import tensorflow as tf; print(tf.config.list_physical_devices(‘GPU’))”]