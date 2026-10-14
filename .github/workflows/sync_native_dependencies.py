"""Reconcile native dependencies into two existing CodeMastery Project fields.

Python standard library + authenticated GitHub CLI. Dry-run is the default.
This module has no Issue, dependency, field-definition or inventory writer.
"""

import argparse
import collections
import contextlib
import datetime
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time

PROJECT_ID = "PVT_kwHODKWgoM4Bmgtn"
OWNER = "chi-thanh-nguyen"
REPOSITORY = "chi-thanh-nguyen/codemastery"
REPOSITORY_ID = "R_kgDOUvTDdg"
FIELDS = {
    "Blocked by": "PVTF_lAHODKWgoM4BmgtnzhlrLhw",
    "Blocking": "PVTF_lAHODKWgoM4BmgtnzhlrLq0",
}
DIRECTIONS = {"Blocked by": "blockedBy", "Blocking": "blocking"}
# Published identities verified from the approved 67-Issue inventory and live Project.
# Inventory expansion/replacement requires a separately reviewed allowlist change.
INVENTORY = (
    ('EP01', 6, 'I_kwDOUvTDds8AAAABWKBjkg'),
    ('EP02', 7, 'I_kwDOUvTDds8AAAABWKHpkQ'),
    ('EP03', 8, 'I_kwDOUvTDds8AAAABWKJedg'),
    ('EP04', 9, 'I_kwDOUvTDds8AAAABWKLCkQ'),
    ('EP05', 10, 'I_kwDOUvTDds8AAAABWKLUsA'),
    ('EP06', 11, 'I_kwDOUvTDds8AAAABWKLuTQ'),
    ('EP07', 12, 'I_kwDOUvTDds8AAAABWKMHhA'),
    ('EP08', 13, 'I_kwDOUvTDds8AAAABWKMhPw'),
    ('EP09', 14, 'I_kwDOUvTDds8AAAABWKM6eg'),
    ('EP10', 15, 'I_kwDOUvTDds8AAAABWKNVBw'),
    ('EP11', 16, 'I_kwDOUvTDds8AAAABWKNzog'),
    ('CT20', 17, 'I_kwDOUvTDds8AAAABWKj4ig'),
    ('CT01', 18, 'I_kwDOUvTDds8AAAABWKkPAw'),
    ('CT16', 19, 'I_kwDOUvTDds8AAAABWKkvUA'),
    ('CT18', 20, 'I_kwDOUvTDds8AAAABWKlQqw'),
    ('CT38', 21, 'I_kwDOUvTDds8AAAABWKlxjA'),
    ('CT03', 22, 'I_kwDOUvTDds8AAAABWKm6TQ'),
    ('CT21', 23, 'I_kwDOUvTDds8AAAABWKngxw'),
    ('CT06', 24, 'I_kwDOUvTDds8AAAABWKoHbg'),
    ('CT24', 25, 'I_kwDOUvTDds8AAAABWKowug'),
    ('CT36', 26, 'I_kwDOUvTDds8AAAABWKqiAA'),
    ('CT14', 27, 'I_kwDOUvTDds8AAAABWKwhNg'),
    ('CT15', 28, 'I_kwDOUvTDds8AAAABWKxcag'),
    ('AU01', 29, 'I_kwDOUvTDds8AAAABWMDniA'),
    ('CO01', 30, 'I_kwDOUvTDds8AAAABWMEAlw'),
    ('AS01', 31, 'I_kwDOUvTDds8AAAABWMEc6A'),
    ('AS04', 32, 'I_kwDOUvTDds8AAAABWME7eg'),
    ('AD01', 33, 'I_kwDOUvTDds8AAAABWMMGrQ'),
    ('AD03', 34, 'I_kwDOUvTDds8AAAABWMMqHg'),
    ('IN01', 35, 'I_kwDOUvTDds8AAAABWMNPMw'),
    ('IN03', 36, 'I_kwDOUvTDds8AAAABWMN2qA'),
    ('FE01', 37, 'I_kwDOUvTDds8AAAABWMOeLw'),
    ('FT01', 38, 'I_kwDOUvTDds8AAAABWMwJTg'),
    ('FT02', 39, 'I_kwDOUvTDds8AAAABWMwZkA'),
    ('AU03', 40, 'I_kwDOUvTDds8AAAABWMwwow'),
    ('CO03', 41, 'I_kwDOUvTDds8AAAABWMxIPQ'),
    ('AD06', 42, 'I_kwDOUvTDds8AAAABWMxhaA'),
    ('CT22', 43, 'I_kwDOUvTDds8AAAABWNI0Eg'),
    ('CT23', 44, 'I_kwDOUvTDds8AAAABWNJGDw'),
    ('CT04', 45, 'I_kwDOUvTDds8AAAABWNJbEQ'),
    ('CT40', 46, 'I_kwDOUvTDds8AAAABWNJx2Q'),
    ('CT05', 47, 'I_kwDOUvTDds8AAAABWNKHjA'),
    ('CT07', 48, 'I_kwDOUvTDds8AAAABWNKf9Q'),
    ('CT08', 49, 'I_kwDOUvTDds8AAAABWNK8gA'),
    ('SH01', 50, 'I_kwDOUvTDds8AAAABWNoLdA'),
    ('AU04', 51, 'I_kwDOUvTDds8AAAABWNoiXg'),
    ('AU05', 52, 'I_kwDOUvTDds8AAAABWNo_lw'),
    ('AU06', 53, 'I_kwDOUvTDds8AAAABWNpaOQ'),
    ('CO04', 54, 'I_kwDOUvTDds8AAAABWNp2PA'),
    ('CO05', 55, 'I_kwDOUvTDds8AAAABWNqRJQ'),
    ('FE03', 56, 'I_kwDOUvTDds8AAAABWNtHkg'),
    ('FE07', 57, 'I_kwDOUvTDds8AAAABWNtuMQ'),
    ('AD09', 58, 'I_kwDOUvTDds8AAAABWNuRBQ'),
    ('SD01', 59, 'I_kwDOUvTDds8AAAABWN_PTg'),
    ('SD02', 60, 'I_kwDOUvTDds8AAAABWN_UJg'),
    ('QA01', 61, 'I_kwDOUvTDds8AAAABWOLELw'),
    ('QA02', 62, 'I_kwDOUvTDds8AAAABWOLVng'),
    ('QA07', 63, 'I_kwDOUvTDds8AAAABWOLrWg'),
    ('QA13', 64, 'I_kwDOUvTDds8AAAABWOL_Vg'),
    ('QA14', 65, 'I_kwDOUvTDds8AAAABWOMVEQ'),
    ('DP02', 66, 'I_kwDOUvTDds8AAAABWOMpqA'),
    ('DO01', 67, 'I_kwDOUvTDds8AAAABWONBVA'),
    ('DO02', 68, 'I_kwDOUvTDds8AAAABWONZiQ'),
    ('DO03', 69, 'I_kwDOUvTDds8AAAABWON0fg'),
    ('CT17', 70, 'I_kwDOUvTDds8AAAABWOWbqQ'),
    ('DP01', 71, 'I_kwDOUvTDds8AAAABWOWmww'),
    ('DP03', 72, 'I_kwDOUvTDds8AAAABWOWyxQ'),
)
EXPECTED = {node: (stable, number) for stable, number, node in INVENTORY}
RELATION = 'id number state repository{id nameWithOwner}'
CONNECTION = ('nodes{' + RELATION + '} totalCount '
              'pageInfo{hasNextPage endCursor}')
TEXT_VALUE = '... on ProjectV2ItemFieldTextValue{text field{... on ProjectV2Field{id}}}'
ITEM = '''id isArchived type content{__typename ... on Issue{
  id number repository{id nameWithOwner}}}
  predecessors:fieldValueByName(name:"Blocked by"){''' + TEXT_VALUE + '''}
  dependents:fieldValueByName(name:"Blocking"){''' + TEXT_VALUE + '''}
  stable:fieldValueByName(name:"Stable ID"){''' + TEXT_VALUE + '} '
PROJECT_QUERY = '''query($id:ID!,$after:String){node(id:$id){... on ProjectV2{
  id number public viewerCanUpdate owner{... on User{login}}
  fields(first:100){pageInfo{hasNextPage} nodes{
    ... on ProjectV2Field{id name dataType}
    ... on ProjectV2SingleSelectField{id name dataType}
    ... on ProjectV2IterationField{id name dataType}}}
  items(first:100,after:$after){totalCount pageInfo{hasNextPage endCursor}
    nodes{''' + ITEM + '}}}}}'
SET_QUERY = '''mutation($input:UpdateProjectV2ItemFieldValueInput!){
  updateProjectV2ItemFieldValue(input:$input){projectV2Item{id}}}'''
CLEAR_QUERY = '''mutation($input:ClearProjectV2ItemFieldValueInput!){
  clearProjectV2ItemFieldValue(input:$input){projectV2Item{id}}}'''


class SyncError(RuntimeError):
    """Fail closed; never interpret failure as an empty connection."""


class RetryableError(SyncError):
    """A mutation may have succeeded. Re-read before a bounded retry."""

    def __init__(self, message, delay=0):
        super().__init__(message)
        self.delay = delay


def require(condition, message):
    if not condition:
        raise SyncError(message)


def normalize(rows):
    """Direct membership only, including closed Issues; never infer transitive edges."""
    identities = {}
    references = {}
    for row in rows:
        repo = row['repository']['nameWithOwner']
        number, node = row['number'], row['id']
        require(isinstance(number, int) and not isinstance(number, bool) and number > 0
                and isinstance(node, str) and node
                and re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo),
                'Invalid native Issue identity')
        ref = (repo, number)
        require(node not in identities or identities[node] == ref,
                'Conflicting native node identity')
        require(ref not in references or references[ref] == node,
                'Conflicting native repository/number identity')
        identities[node], references[ref] = ref, node
    ordered = sorted(references, key=lambda r: (r[0] != REPOSITORY,
                                               '' if r[0] == REPOSITORY else r[0], r[1]))
    return ', '.join(f'#{n}' if r == REPOSITORY else f'{r}#{n}' for r, n in ordered)


def read_connection(fetch):
    """Exhaust pages; reject changed counts, missing nodes, duplicates and cursor loops."""
    rows, seen, cursors = [], set(), set()
    cursor, total = None, None
    while True:
        page = fetch(cursor)
        require(isinstance(page, dict) and isinstance(page.get('nodes'), list),
                'Missing connection; preserve existing values')
        count = page.get('totalCount')
        require(isinstance(count, int) and count >= 0, 'Invalid connection count')
        if total is None:
            total = count
        require(total == count, 'Connection changed during pagination; retry later')
        for row in page['nodes']:
            require(isinstance(row, dict) and row.get('id') and row['id'] not in seen,
                    'Null or duplicate connection member')
            seen.add(row['id'])
            rows.append(row)
        info = page.get('pageInfo', {})
        require(isinstance(info.get('hasNextPage'), bool), 'Missing pagination state')
        if not info['hasNextPage']:
            require(len(rows) == total, 'Incomplete connection; preserve existing values')
            return rows
        cursor = info.get('endCursor')
        require(cursor and cursor not in cursors, 'Missing/repeated pagination cursor')
        cursors.add(cursor)


def check_native(native):
    """Check reciprocal internal membership and cycles without writing native links."""
    edges = set()
    for node, issue in native.items():
        for direction in DIRECTIONS.values():
            normalize(issue[direction])
            for related in issue[direction]:
                other = related['id']
                require(other != node, 'Native self-dependency; explicit review required')
                edge = (other, node) if direction == 'blockedBy' else (node, other)
                edges.add(edge)
                if other in native:
                    inverse = 'blocking' if direction == 'blockedBy' else 'blockedBy'
                    require(node in {r['id'] for r in native[other][inverse]},
                            'Native directions changed or disagree; retry later')
    graph, incoming = collections.defaultdict(set), collections.Counter()
    for a, b in edges:
        graph[a].add(b)
        incoming[b] += 1
        incoming.setdefault(a, 0)
    queue = [node for node, count in incoming.items() if count == 0]
    visited = 0
    while queue:
        node = queue.pop()
        visited += 1
        for other in graph[node]:
            incoming[other] -= 1
            if incoming[other] == 0:
                queue.append(other)
    require(visited == len(incoming), 'Native cycle; preserve links for explicit review')
    return len(edges)


def field_value(item, name):
    alias = 'predecessors' if name == 'Blocked by' else 'dependents'
    value = item[alias]
    if value is None:
        return ''
    require(value.get('field', {}).get('id') == FIELDS[name]
            and isinstance(value.get('text'), str), 'Incompatible derived field value')
    return value['text']


class Client:
    """Fixed-host GraphQL transport. No raw responses or credentials enter logs."""

    def __init__(self, output, apply=False):
        self.output = Path(output)
        self.output.mkdir(mode=0o700, parents=True, exist_ok=False)
        os.chmod(self.output, 0o700)
        self.apply = apply
        self.allowed_items = set()
        self.mutations = 0

    def record(self, name, data):
        path = self.output / name
        with path.open('w', encoding='utf-8') as stream:
            os.chmod(path, 0o600)
            json.dump(data, stream, indent=2)
            stream.write('\n')

    def event(self, **data):
        path = self.output / 'journal.jsonl'
        with path.open('a', encoding='utf-8') as stream:
            os.chmod(path, 0o600)
            stream.write(json.dumps({
                'time': datetime.datetime.now(datetime.timezone.utc).isoformat(), **data
            }) + '\n')

    def gql(self, query, variables=None):
        variables = variables or {}
        mutation = query.lstrip().startswith('mutation')
        require(mutation or (query.lstrip().startswith('query')
                             and not re.search(r'\bmutation\b', query)),
                'Only explicit query operations or allowlisted mutations are permitted')
        if mutation:
            require(self.apply and query in (SET_QUERY, CLEAR_QUERY),
                    'Only explicit apply may mutate the two existing text values')
            value = variables.get('input', {})
            require(value.get('projectId') == PROJECT_ID
                    and value.get('fieldId') in FIELDS.values()
                    and value.get('itemId') in self.allowed_items,
                    'Mutation target outside allowlist')
            keys = {'projectId', 'itemId', 'fieldId'}
            if query == SET_QUERY:
                keys.add('value')
                require(set(value.get('value', {})) == {'text'}
                        and isinstance(value['value']['text'], str), 'Invalid text update')
            require(set(value) == keys, 'Unexpected mutation property')
        for attempt in range(3):
            try:
                response = subprocess.run(
                    ['gh', 'api', '--hostname', 'github.com', 'graphql', '--include',
                     '--input', '-'],
                    input=json.dumps({'query': query, 'variables': variables}),
                    capture_output=True, text=True, timeout=45,
                    env={**os.environ, 'GH_DEBUG': '', 'GH_PAGER': 'cat'})
            except subprocess.TimeoutExpired:
                if mutation or attempt == 2:
                    raise RetryableError('API timeout; re-read before retry') from None
                time.sleep(2 ** attempt)
                continue
            # gh --include emits HTTP headers before the JSON body.
            match = re.search(r'(?m)^\{', response.stdout)
            status = re.search(r'(?m)^HTTP/\S+\s+(\d+)', response.stdout)
            code = int(status.group(1)) if status else None
            headers = dict((k.lower(), v.strip()) for k, v in re.findall(
                r'(?mi)^([a-z0-9-]+):\s*([^\r\n]*)', response.stdout[:match.start()]
                if match else response.stdout))
            try:
                result = json.loads(response.stdout[match.start():]) if match else {}
            except ValueError:
                result = {}
            if (response.returncode == 0 and result.get('data') is not None
                    and not result.get('errors')):
                if mutation:
                    self.mutations += 1
                return result['data']
            errors = result.get('errors', [])
            kinds = {str(e.get('type', '')) for e in errors if isinstance(e, dict)}
            transient = (code in (429, 500, 502, 503, 504)
                         or 'retry-after' in headers
                         or headers.get('x-ratelimit-remaining') == '0'
                         or bool(kinds & {'RATE_LIMITED', 'INTERNAL', 'SERVICE_UNAVAILABLE'})
                         or (code is None and not errors))
            # Raw stderr/messages can contain private data; only classifications are logged.
            self.event(stage='API_FAILURE', http_status=code, retryable=transient,
                       mutation=mutation, attempt=attempt + 1)
            if not transient:
                raise SyncError('API authorization/schema/validation failure; no inferred empties')
            try:
                delay = float(headers.get('retry-after', 2 ** (attempt + 1)))
                if headers.get('x-ratelimit-remaining') == '0':
                    delay = max(delay, float(headers.get('x-ratelimit-reset', time.time()))
                                - time.time())
            except ValueError:
                raise SyncError('Invalid rate-limit response; resume later') from None
            require(delay <= 60, 'Rate-limit cooldown exceeds retry budget; resume later')
            if mutation or attempt == 2:
                raise RetryableError('Transient/uncertain API outcome; re-read before retry',
                                     max(1, delay))
            time.sleep(max(1, delay))
        raise SyncError('Read retry budget exhausted')


def project(client):
    metadata = None

    def fetch(cursor):
        nonlocal metadata
        row = client.gql(PROJECT_QUERY, {'id': PROJECT_ID, 'after': cursor}).get('node')
        require(row and row['id'] == PROJECT_ID and row['number'] == 3
                and row['owner'].get('login') == OWNER and row['public'] is False,
                'Project identity/visibility changed')
        require(not row['fields']['pageInfo']['hasNextPage'], 'Incomplete field inventory')
        for name, field_id in FIELDS.items():
            found = [f for f in row['fields']['nodes'] if f.get('name') == name]
            require(len(found) == 1 and found[0].get('id') == field_id
                    and found[0].get('dataType') == 'TEXT',
                    'Missing/replaced/duplicate/incompatible derived field')
        metadata = row
        return row['items']

    items = read_connection(fetch)
    mapped = {}
    for item in items:
        content = item.get('content')
        require(item['type'] == 'ISSUE' and not item['isArchived'] and content
                and content.get('__typename') == 'Issue', 'Unexpected non-Issue/archived item')
        node = content.get('id')
        require(node in EXPECTED and node not in mapped, 'Unknown/duplicate Project Issue')
        stable, number = EXPECTED[node]
        require(content['number'] == number
                and content['repository'] == {'id': REPOSITORY_ID, 'nameWithOwner': REPOSITORY},
                'Project Issue identity mismatch')
        require(item.get('stable') and item['stable'].get('text') == stable,
                'Stable ID missing or changed')
        for name in FIELDS:
            field_value(item, name)
        mapped[node] = item
    require(set(mapped) == set(EXPECTED), 'Project inventory differs from approved 67 Issues')
    return mapped, metadata['viewerCanUpdate']


def native(client, ids):
    result = {}
    for start in range(0, len(ids), 12):
        variables = {f'i{j}': node for j, node in enumerate(ids[start:start + 12])}
        query = ('query(' + ','.join('$' + key + ':ID!' for key in variables) + '){'
                 + ''.join(key + ':node(id:$' + key + '){... on Issue{' + RELATION
                           + ' blockedBy(first:100){' + CONNECTION + '}'
                           + ' blocking(first:100){' + CONNECTION + '}}}'
                           for key in variables) + '}')
        batch = client.gql(query, variables)
        for key, node in variables.items():
            issue = batch.get(key)
            require(issue and issue['id'] == node and issue['number'] == EXPECTED[node][1]
                    and issue['repository'] == {'id': REPOSITORY_ID,
                                                'nameWithOwner': REPOSITORY},
                    'Missing/conflicting source Issue')
            for direction in DIRECTIONS.values():
                first = issue[direction]

                def fetch(cursor, direction=direction, node=node, first=first):
                    if cursor is None:
                        return first
                    data = client.gql(
                        'query($id:ID!,$after:String){node(id:$id){... on Issue{'
                        + direction + '(first:100,after:$after){' + CONNECTION + '}}}}',
                        {'id': node, 'after': cursor})
                    require(data.get('node'), 'Source Issue disappeared during pagination')
                    return data['node'].get(direction)

                issue[direction] = read_connection(fetch)
            result[node] = issue
    check_native(result)
    return result


def reconcile(client, apply=False):
    require(not apply or client.apply, 'Client must explicitly authorize apply')
    items, writable = project(client)
    require(not apply or writable, 'Principal cannot update Project')
    client.allowed_items = {item['id'] for item in items.values()}
    truth = native(client, list(EXPECTED))
    planned = changed = 0
    for stable, number, node in INVENTORY:
        for name, direction in DIRECTIONS.items():
            target = normalize(truth[node][direction])
            if target == field_value(items[node], name):
                continue
            planned += 1
            if not apply:
                continue
            # No compare-and-swap exists for these fields. Re-read and recompute each attempt.
            for attempt in range(3):
                fresh_items, writable = project(client)
                require(writable and fresh_items[node]['id'] == items[node]['id'],
                        'Write permission or Project item identity changed')
                current = field_value(fresh_items[node], name)
                target = normalize(native(client, [node])[node][direction])
                if current == target:
                    break
                value = {'projectId': PROJECT_ID, 'itemId': items[node]['id'],
                         'fieldId': FIELDS[name]}
                query = CLEAR_QUERY
                if target:
                    query = SET_QUERY
                    value['value'] = {'text': target}
                client.event(stage='VALUE_INTENT', number=number, stable_id=stable,
                             field=name, previous=current, expected=target, attempt=attempt + 1)
                try:
                    client.gql(query, {'input': value})
                except RetryableError as error:
                    # Unknown outcome is resolved by reading both services, never blind replay.
                    client.event(stage='UNCERTAIN_WRITE', number=number, field=name)
                    time.sleep(error.delay)
                after, _ = project(client)
                require(after[node]['id'] == items[node]['id'], 'Item replaced during write')
                actual = field_value(after[node], name)
                latest = normalize(native(client, [node])[node][direction])
                client.event(stage='VALUE_READBACK', number=number, field=name,
                             actual=actual, expected=latest, verified=actual == latest)
                if actual == latest:
                    changed += 1
                    break
                if attempt == 2:
                    raise SyncError('Concurrent changes did not converge; resume reconciliation')
                time.sleep(attempt + 1)
    # Independent, full current reads; successful mutation responses are insufficient.
    final_items, _ = project(client)
    final_native = native(client, list(EXPECTED))
    mismatches = sum(field_value(final_items[node], name)
                     != normalize(final_native[node][direction])
                     for node in EXPECTED for name, direction in DIRECTIONS.items())
    summary = {'mode': 'apply' if apply else 'dry-run', 'issues': len(EXPECTED),
               'cells': 2 * len(EXPECTED), 'planned_changes': planned,
               'verified_changes': changed, 'confirmed_mutations': client.mutations,
               'mismatched_cells': mismatches,
               'empty_cells': sum(not final_native[node][direction] for node in EXPECTED
                                  for direction in DIRECTIONS.values()),
               'status': ('PASS' if not mismatches else
                          'DRIFT_DETECTED' if not apply else 'PARTIAL')}
    # Do not publish native references, Project metadata or private old values in Actions logs.
    client.record('summary.json', summary)
    return summary


@contextlib.contextmanager
def local_lock():
    """Protect simultaneous processes on one host; Actions concurrency spans runners."""
    path = Path(tempfile.gettempdir()) / 'codemastery-project-3-dependency-sync.lock'
    descriptor = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SyncError('Another local reconciliation is running') from None
        yield
    finally:
        os.close(descriptor)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Write only the two derived fields')
    parser.add_argument('--output-dir', required=True, type=Path,
                        help='New private directory outside the repository; never upload publicly')
    args = parser.parse_args()
    require(not args.apply or os.environ.get('CODEMASTERY_DEPENDENCY_SYNC_ENABLED') == 'true',
            'Apply requires separate Leader activation via CODEMASTERY_DEPENDENCY_SYNC_ENABLED')
    output = args.output_dir.resolve()
    repo = Path(__file__).resolve().parents[2]
    require(output != repo and repo not in output.parents, 'Evidence must remain outside repository')
    client = Client(output, apply=args.apply)
    try:
        with local_lock():
            summary = reconcile(client, args.apply)
        print(json.dumps(summary, sort_keys=True))
        return 0 if summary['status'] == 'PASS' else 2
    except (SyncError, KeyError, TypeError, ValueError, OSError):
        # No traceback/HTTP body/private values in public logs. Private journal retains prior writes.
        client.event(stage='STOPPED', rule='Preserve values on incomplete/failed reads; resume safely')
        print('Reconciliation stopped; no empty lists inferred from failed reads. '
              'Earlier or uncertain writes may remain; inspect the private journal '
              'and re-read current state before recovery.')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
