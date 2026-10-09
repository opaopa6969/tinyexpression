#!/usr/bin/env python3
"""Verify the packaged existing launcher, opt-in provider service, and actual javac diagnostics."""
import json
import pathlib
import subprocess
import threading
import queue

root = pathlib.Path(__file__).resolve().parents[1]
messages = queue.Queue()
with (root / 'target/embedded-stdio.log').open('wb') as errors:
    process = subprocess.Popen(['java', '--enable-preview', '-jar', str(root / 'target/tinyexpression-p4-lsp-server.jar')], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors)
    def read():
        try:
            while True:
                headers = {}
                while True:
                    line = process.stdout.readline()
                    if not line:
                        raise EOFError('server exited')
                    if line == b'\r\n':
                        break
                    key, value = line.decode('ascii').split(':', 1)
                    headers[key.lower()] = value.strip()
                messages.put(json.loads(process.stdout.read(int(headers['content-length']))))
        except BaseException as error:
            messages.put(error)
    threading.Thread(target=read, daemon=True).start()
    def send(method, params, request_id=None):
        message = {'jsonrpc': '2.0', 'method': method, 'params': params}
        if request_id is not None:
            message['id'] = request_id
        body = json.dumps(message, ensure_ascii=False).encode('utf-8')
        process.stdin.write(f'Content-Length: {len(body)}\r\n\r\n'.encode('ascii') + body)
        process.stdin.flush()
    def receive(predicate):
        while True:
            message = messages.get(timeout=40)
            if isinstance(message, BaseException):
                raise message
            if predicate(message):
                return message
    try:
        send('initialize', {'capabilities': {}, 'initializationOptions': {'embeddedLanguageDiagnostics': True}}, 1)
        initialize = receive(lambda value: value.get('id') == 1)
        assert initialize['result']['capabilities']['experimental']['embeddedLanguageDiagnostics']['enabled']
        send('initialized', {})
        uri = 'file:///production.formulainfo'
        send('textDocument/didOpen', {'textDocument': {'uri': uri, 'languageId': 'tinyexpressionP4', 'version': 1, 'text': (root / 'src/embedded-test/resources/type-error.txt').read_text()}})
        published = receive(lambda value: value.get('method') == 'textDocument/publishDiagnostics')['params']
        assert published['version'] == 1, published
        diagnostics = [item for item in published['diagnostics'] if item.get('source') == 'tinyexpression-java']
        assert [(item['range']['start']['line'], item['range']['start']['character']) for item in diagnostics] == [(41, 9), (69, 9)], published
        assert all(item['code'] == 'compiler.err.prob.found.req' and item['severity'] == 1 for item in diagnostics)
        send('textDocument/didClose', {'textDocument': {'uri': uri}})
        assert receive(lambda value: value.get('method') == 'textDocument/publishDiagnostics')['params']['diagnostics'] == []
        send('shutdown', {}, 2)
        receive(lambda value: value.get('id') == 2)
        send('exit', {})
        print('PASS: packaged Tiny P4 launcher -> real javac -> two original FormulaInfo diagnostic positions -> close clear')
    finally:
        process.kill()
        process.wait(timeout=5)
