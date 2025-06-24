import 'dart:developer';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:face_recognition_flutter_plugin/face_recognition_view.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  Uint8List? _refImageBytes;

  Future<void> pickReferenceImage() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.image,
        withData: true,
      );
      if (result != null && result.files.single.bytes != null) {
        setState(() {
          _refImageBytes = result.files.single.bytes!;
        });
      } else {
        log("No image selected");
      }
    } catch (e) {
      log("Error picking image: $e");
    }
  }

  @override
  Widget build(BuildContext context) {

    const instructions = [
      '1. Tap the button below to select a reference face image.',
      '2. Ensure the face is clearly visible and well-lit.',
      '3. The camera will match faces in real-time.'
    ];

    return Scaffold(
      body: (_refImageBytes==null)?
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.face, size: 56,),
                SizedBox(height: 24,),
                ...instructions.map((i)=> Text(i)),
                SizedBox(height: 24,),
                FilledButton(
                  onPressed: pickReferenceImage,
                  child: const Text('Select Reference Image'),
                ),
              ],
            ),
          ):
      SafeArea(
        child: Expanded(
                child: FaceRecognitionView(
                  refImageBytes: _refImageBytes!,
                  threshold: 0.85,
                  showRefPreview: true,
                  rotateDegrees: 0,
                  width: double.infinity,
                  height: double.infinity,
                  onSimilarity: (similarity) {
                    log(similarity.toString(), name: 'FlutterSimilarity');
                  },
              )
        ),
      ),
    );
  }
}