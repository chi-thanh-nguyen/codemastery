"""Offline safety/behavior tests. Synthetic relationships never reach GitHub."""

import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import sync_native_dependencies as sync


def issue(node, number, repo=sync.REPOSITORY, state='OPEN'):
    return {'id': node, 'number': number, 'state': state,
            'repository': {'id': sync.REPOSITORY_ID, 'nameWithOwner': repo}}


def page(rows, more=False, cursor=None, total=None):
    return {'nodes': rows, 'totalCount': len(rows) if total is None else total,
            'pageInfo': {'hasNextPage': more, 'endCursor': cursor}}


def empty_native():
    return {node: {**issue(node, number), 'blockedBy': [], 'blocking': []}
            for _, number, node in sync.INVENTORY}


def project_items():
    return {node: {
        'id': 'item-' + str(number), 'type': 'ISSUE', 'isArchived': False,
        'content': {'__typename': 'Issue', **issue(node, number)},
        'stable': {'text': stable}, 'predecessors': None, 'dependents': None,
    } for stable, number, node in sync.INVENTORY}


def project_response(items=None):
    return {'id': sync.PROJECT_ID, 'number': 3, 'public': False,
            'viewerCanUpdate': True, 'owner': {'login': sync.OWNER},
            'fields': {'pageInfo': {'hasNextPage': False}, 'nodes': [
                {'name': name, 'id': field_id, 'dataType': 'TEXT'}
                for name, field_id in sync.FIELDS.items()]},
            'items': page(list((items or project_items()).values()))}


def set_value(items, node, name, value):
    alias = 'predecessors' if name == 'Blocked by' else 'dependents'
    items[node][alias] = (None if value is None else
                          {'text': value, 'field': {'id': sync.FIELDS[name]}})


class NormalizationTests(unittest.TestCase):
    def test_sort_deduplicate_and_qualify(self):
        values = [issue('b', 24), issue('a', 6), issue('a', 6),
                  issue('x', 1, 'other/repo')]
        self.assertEqual(sync.normalize(values), '#6, #24, other/repo#1')

    def test_closed_and_reopened_membership_unchanged(self):
        row = issue('a', 6, state='CLOSED')
        self.assertEqual(sync.normalize([row]), '#6')
        row['state'] = 'OPEN'
        self.assertEqual(sync.normalize([row]), '#6')

    def test_conflicting_node_and_reference_rejected(self):
        for rows in ([issue('a', 6), issue('a', 7)],
                     [issue('a', 6), issue('b', 6)]):
            with self.assertRaises(sync.SyncError):
                sync.normalize(rows)

    def test_empty(self):
        self.assertEqual(sync.normalize([]), '')

    def test_invalid_identity(self):
        with self.assertRaises(sync.SyncError):
            sync.normalize([issue('a', 0)])


class PaginationTests(unittest.TestCase):
    def test_exhaust_pages(self):
        fetch = Mock(side_effect=[page([{'id': 'a'}], True, 'next', 2),
                                  page([{'id': 'b'}], total=2)])
        self.assertEqual(len(sync.read_connection(fetch)), 2)
        self.assertEqual(fetch.call_args_list[1].args, ('next',))

    def test_duplicate_missing_cursor_changed_count_and_incomplete_rejected(self):
        cases = [
            [page([{'id': 'a'}], True, 'x', 2), page([{'id': 'a'}], total=2)],
            [page([], True, None, 1)],
            [page([{'id': 'a'}], True, 'x', 2), page([{'id': 'b'}], total=3)],
            [page([], total=1)],
            [page([], True, 'x', 1), page([], True, 'x', 1)],
            [None],
        ]
        for pages in cases:
            with self.subTest(pages=pages), self.assertRaises(sync.SyncError):
                sync.read_connection(Mock(side_effect=pages))

    def test_page_failure_never_becomes_empty(self):
        with self.assertRaises(sync.SyncError):
            sync.read_connection(Mock(side_effect=[page([], True, 'x', 1),
                                                   sync.SyncError('denied')]))


class GraphTests(unittest.TestCase):
    def test_reciprocal_and_direct_only(self):
        truth = empty_native()
        a, b, c = list(truth)[:3]
        for left, right in ((a, b), (b, c)):
            truth[left]['blocking'].append(issue(right, sync.EXPECTED[right][1]))
            truth[right]['blockedBy'].append(issue(left, sync.EXPECTED[left][1]))
        self.assertEqual(sync.check_native(truth), 2)
        self.assertNotIn('#8', sync.normalize(truth[a]['blocking']))

    def test_cycle_self_and_missing_inverse_rejected(self):
        truth = empty_native()
        a, b = list(truth)[:2]
        truth[a]['blocking'] = [issue(b, sync.EXPECTED[b][1])]
        with self.assertRaises(sync.SyncError):
            sync.check_native(truth)
        truth[b]['blockedBy'] = [issue(a, sync.EXPECTED[a][1])]
        truth[b]['blocking'] = [issue(a, sync.EXPECTED[a][1])]
        truth[a]['blockedBy'] = [issue(b, sync.EXPECTED[b][1])]
        with self.assertRaises(sync.SyncError):
            sync.check_native(truth)
        with self.assertRaises(sync.SyncError):
            sync.check_native({a: {**truth[a], 'blockedBy': [issue(a, 6)], 'blocking': []}})


class IdentityTests(unittest.TestCase):
    def test_inventory_is_exactly_the_67_published_issues(self):
        self.assertEqual(len(sync.INVENTORY), 67)
        self.assertEqual(len(sync.EXPECTED), 67)
        self.assertEqual({n for _, n, _ in sync.INVENTORY}, set(range(6, 73)))
        self.assertEqual(sum(stable.startswith('EP') for stable, _, _ in sync.INVENTORY), 11)

    def test_verified_project(self):
        client = Mock()
        client.gql.return_value = {'node': project_response()}
        items, writable = sync.project(client)
        self.assertEqual(len(items), 67)
        self.assertTrue(writable)

    def test_missing_item_wrong_field_and_unexpected_item_fail_closed(self):
        for change in ('missing', 'field', 'duplicate', 'pr', 'stable', 'public'):
            data = project_response()
            if change == 'missing':
                data['items']['nodes'].pop()
                data['items']['totalCount'] -= 1
            elif change == 'field':
                data['fields']['nodes'][0]['id'] = 'replacement-field'
            elif change == 'duplicate':
                data['items']['nodes'][1]['content'] = data['items']['nodes'][0]['content']
            elif change == 'pr':
                data['items']['nodes'][0]['type'] = 'PULL_REQUEST'
            elif change == 'stable':
                data['items']['nodes'][0]['stable']['text'] = 'wrong'
            else:
                data['public'] = True
            client = Mock()
            client.gql.return_value = {'node': data}
            with self.subTest(change=change), self.assertRaises(sync.SyncError):
                sync.project(client)

    def test_project_pagination(self):
        first = project_response()
        second = copy.deepcopy(first)
        rows = first['items']['nodes']
        first['items'] = page(rows[:30], True, 'next', 67)
        second['items'] = page(rows[30:], total=67)
        client = Mock()
        client.gql.side_effect = [{'node': first}, {'node': second}]
        self.assertEqual(len(sync.project(client)[0]), 67)

    def test_native_paginated_both_directions(self):
        node = sync.INVENTORY[0][2]
        data = {**issue(node, 6),
                'blockedBy': page([issue('a', 20, 'other/repo')], True, 'next', 2),
                'blocking': page([])}
        client = Mock()
        client.gql.side_effect = [
            {'i0': data}, {'node': {'blockedBy': page([issue('b', 21, 'other/repo')], total=2)}}]
        self.assertEqual(len(sync.native(client, [node])[node]['blockedBy']), 2)

    def test_inaccessible_native_issue_fails(self):
        client = Mock()
        client.gql.return_value = {'i0': None}
        with self.assertRaises(sync.SyncError):
            sync.native(client, [sync.INVENTORY[0][2]])


class ReconcileTests(unittest.TestCase):
    def setUp(self):
        self.items, self.truth = project_items(), empty_native()
        self.a, self.b = list(self.truth)[:2]
        self.client = Mock(apply=True, mutations=0)
        self.mutations = []
        self.project = patch.object(sync, 'project', side_effect=lambda _: (self.items, True))
        self.native = patch.object(sync, 'native', side_effect=lambda _, ids: {
            node: self.truth[node] for node in ids})
        self.project.start()
        self.native.start()
        self.addCleanup(self.project.stop)
        self.addCleanup(self.native.stop)
        self.client.gql.side_effect = self.write

    def write(self, query, variables):
        value = variables['input']
        self.assertEqual(value['projectId'], sync.PROJECT_ID)
        self.assertIn(value['fieldId'], sync.FIELDS.values())
        node = next(n for n, item in self.items.items() if item['id'] == value['itemId'])
        name = next(n for n, field_id in sync.FIELDS.items() if field_id == value['fieldId'])
        self.mutations.append((query, copy.deepcopy(value)))
        set_value(self.items, node, name, value.get('value', {}).get('text'))
        self.client.mutations += 1
        return {}

    def edge(self):
        self.truth[self.a]['blocking'] = [issue(self.b, sync.EXPECTED[self.b][1])]
        self.truth[self.b]['blockedBy'] = [issue(self.a, sync.EXPECTED[self.a][1])]

    def test_empty_inventory_zero_writes_and_repeat_idempotency(self):
        for _ in range(2):
            result = sync.reconcile(self.client, apply=True)
            self.assertEqual(result['status'], 'PASS')
            self.assertEqual(result['empty_cells'], 134)
        self.assertEqual(self.mutations, [])

    def test_dry_run_detects_drift_without_writing(self):
        self.edge()
        result = sync.reconcile(self.client)
        self.assertEqual(result['status'], 'DRIFT_DETECTED')
        self.assertEqual(result['planned_changes'], 2)
        self.assertEqual(self.mutations, [])

    def test_add_multiple_directions_and_idempotency(self):
        self.edge()
        self.assertEqual(sync.reconcile(self.client, True)['mismatched_cells'], 0)
        self.assertEqual(len(self.mutations), 2)
        sync.reconcile(self.client, True)
        self.assertEqual(len(self.mutations), 2)

    def test_verified_removed_membership_clears_only_two_values(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        result = sync.reconcile(self.client, True)
        self.assertEqual(result['verified_changes'], 1)
        self.assertEqual(self.mutations[0][0], sync.CLEAR_QUERY)

    def test_initial_read_failure_preserves_old_value(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        with patch.object(sync, 'native', side_effect=sync.SyncError('denied')):
            with self.assertRaises(sync.SyncError):
                sync.reconcile(self.client, True)
        self.assertEqual(sync.field_value(self.items[self.a], 'Blocked by'), '#20')
        self.assertEqual(self.mutations, [])

    def test_reread_failure_never_clears(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        calls = [self.truth, sync.SyncError('incomplete')]
        with patch.object(sync, 'native', side_effect=calls):
            with self.assertRaises(sync.SyncError):
                sync.reconcile(self.client, True)
        self.assertEqual(self.mutations, [])

    def test_timeout_after_success_readback_prevents_duplicate_mutation(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        def uncertain(query, variables):
            self.write(query, variables)
            raise sync.RetryableError('timeout')
        self.client.gql.side_effect = uncertain
        self.assertEqual(sync.reconcile(self.client, True)['status'], 'PASS')
        self.assertEqual(len(self.mutations), 1)

    def test_timeout_before_success_recomputes_and_retries(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        attempts = []
        def uncertain(query, variables):
            attempts.append(1)
            if len(attempts) == 1:
                raise sync.RetryableError('timeout')
            return self.write(query, variables)
        self.client.gql.side_effect = uncertain
        with patch.object(sync.time, 'sleep'):
            self.assertEqual(sync.reconcile(self.client, True)['status'], 'PASS')
        self.assertEqual(len(attempts), 2)
        self.assertEqual(len(self.mutations), 1)

    def test_permission_failure_is_not_retried(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        self.client.gql.side_effect = sync.SyncError('denied')
        with self.assertRaises(sync.SyncError):
            sync.reconcile(self.client, True)
        self.assertEqual(self.client.gql.call_count, 1)

    def test_partial_failure_preserves_verified_first_direction_for_resume(self):
        self.edge()
        calls = []
        def partial(query, variables):
            calls.append(1)
            if len(calls) == 2:
                raise sync.SyncError('permission lost')
            return self.write(query, variables)
        self.client.gql.side_effect = partial
        with self.assertRaises(sync.SyncError):
            sync.reconcile(self.client, True)
        self.assertEqual(len(self.mutations), 1)
        self.client.gql.side_effect = self.write
        self.assertEqual(sync.reconcile(self.client, True)['status'], 'PASS')
        self.assertEqual(len(self.mutations), 2)

    def test_item_replacement_before_write_is_not_overwritten(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        fresh = copy.deepcopy(self.items)
        fresh[self.a]['id'] = 'replacement-item'
        with patch.object(sync, 'project', side_effect=[(self.items, True), (fresh, True)]):
            with self.assertRaises(sync.SyncError):
                sync.reconcile(self.client, True)
        self.assertEqual(self.mutations, [])

    def test_native_change_before_write_uses_latest_truth(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        def changing(_, ids):
            result = {node: self.truth[node] for node in ids}
            if len(ids) == 1:
                self.truth[self.a]['blockedBy'] = [issue('external', 24, 'other/repo')]
            return result
        with patch.object(sync, 'native', side_effect=changing):
            self.assertEqual(sync.reconcile(self.client, True)['status'], 'PASS')
        self.assertEqual(self.mutations[0][0], sync.SET_QUERY)
        self.assertEqual(self.mutations[0][1]['value'], {'text': 'other/repo#24'})

    def test_unconverged_readback_stops_after_bounded_retries(self):
        set_value(self.items, self.a, 'Blocked by', '#20')
        self.client.gql.side_effect = lambda *args: {}
        with patch.object(sync.time, 'sleep'), self.assertRaises(sync.SyncError):
            sync.reconcile(self.client, True)
        self.assertEqual(self.client.gql.call_count, 3)

    def test_final_independent_read_detects_late_drift(self):
        snapshots = [self.truth, copy.deepcopy(self.truth)]
        snapshots[-1][self.a]['blockedBy'] = [issue('other', 10, 'other/repo')]
        with patch.object(sync, 'native', side_effect=snapshots):
            self.assertEqual(sync.reconcile(self.client)['status'], 'DRIFT_DETECTED')


class TransportTests(unittest.TestCase):
    def client(self, apply=False):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        client = sync.Client(Path(folder.name) / 'evidence', apply)
        client.allowed_items = {'item'}
        return client

    @staticmethod
    def response(code=200, body=None, headers=''):
        body = {'data': {'ok': True}} if body is None else body
        return subprocess.CompletedProcess([], 0 if code == 200 else 1,
                                           f'HTTP/2.0 {code} Test\n{headers}\n{json.dumps(body)}', '')

    def test_fixed_host_no_secret_in_arguments_and_no_raw_response_logs(self):
        client = self.client()
        with patch.object(sync.subprocess, 'run', return_value=self.response()) as run:
            self.assertEqual(client.gql('query{viewer{login}}'), {'ok': True})
        self.assertEqual(run.call_args.args[0][2:4], ['--hostname', 'github.com'])
        self.assertFalse(list(client.output.glob('*response*')))

    def test_read_retries_transient_error(self):
        client = self.client()
        with patch.object(sync.time, 'sleep'), patch.object(sync.subprocess, 'run', side_effect=[
                self.response(503, {'errors': []}), self.response()] ) as run:
            client.gql('query{viewer{login}}')
        self.assertEqual(run.call_count, 2)

    def test_rate_limit_stops_instead_of_unbounded_sleep(self):
        client = self.client()
        with patch.object(sync.subprocess, 'run', return_value=self.response(
                429, {'errors': []}, 'Retry-After: 120\n')):
            with self.assertRaises(sync.SyncError):
                client.gql('query{viewer{login}}')

    def test_partial_graphql_error_is_failure_not_empty(self):
        client = self.client()
        with patch.object(sync.subprocess, 'run', return_value=self.response(body={
                'data': {'node': None}, 'errors': [{'type': 'FORBIDDEN'}]})):
            with self.assertRaises(sync.SyncError):
                client.gql('query{node{id}}')

    def test_mutation_rate_limit_retains_cooldown_without_blind_retry(self):
        client = self.client(True)
        value = {'projectId': sync.PROJECT_ID, 'itemId': 'item',
                 'fieldId': sync.FIELDS['Blocked by']}
        with patch.object(sync.subprocess, 'run', return_value=self.response(
                429, {'errors': []}, 'Retry-After: 12\n')) as run:
            with self.assertRaises(sync.RetryableError) as caught:
                client.gql(sync.CLEAR_QUERY, {'input': value})
        self.assertEqual(caught.exception.delay, 12)
        self.assertEqual(run.call_count, 1)

    def test_long_mutation_cooldown_stops_for_later_run(self):
        client = self.client(True)
        value = {'projectId': sync.PROJECT_ID, 'itemId': 'item',
                 'fieldId': sync.FIELDS['Blocked by']}
        with patch.object(sync.subprocess, 'run', return_value=self.response(
                429, {'errors': []}, 'Retry-After: 120\n')):
            with self.assertRaises(sync.SyncError) as caught:
                client.gql(sync.CLEAR_QUERY, {'input': value})
        self.assertNotIsInstance(caught.exception, sync.RetryableError)

    def test_transport_never_blindly_retries_mutation(self):
        client = self.client(True)
        value = {'projectId': sync.PROJECT_ID, 'itemId': 'item',
                 'fieldId': sync.FIELDS['Blocked by']}
        with patch.object(sync.subprocess, 'run', side_effect=subprocess.TimeoutExpired('gh', 45)) as run:
            with self.assertRaises(sync.RetryableError):
                client.gql(sync.CLEAR_QUERY, {'input': value})
        self.assertEqual(run.call_count, 1)

    def test_unauthorized_mutations_never_reach_network(self):
        value = {'projectId': sync.PROJECT_ID, 'itemId': 'item',
                 'fieldId': sync.FIELDS['Blocked by']}
        cases = [(False, sync.CLEAR_QUERY, value),
                 (True, 'mutation{deleteIssue(input:{}){clientMutationId}}', value),
                 (True, '# comment\nmutation{deleteIssue(input:{}){clientMutationId}}', value),
                 (True, sync.CLEAR_QUERY, {**value, 'fieldId': 'unrelated'}),
                 (True, sync.CLEAR_QUERY, {**value, 'projectId': 'other'}),
                 (True, sync.CLEAR_QUERY, {**value, 'itemId': 'unapproved'}),
                 (True, sync.CLEAR_QUERY, {**value, 'extra': 1}),
                 (True, sync.SET_QUERY, {**value, 'value': {'number': 1}})]
        for apply, query, variables in cases:
            with self.subTest(query=query, value=variables):
                client = self.client(apply)
                with patch.object(sync.subprocess, 'run') as run, self.assertRaises(sync.SyncError):
                    client.gql(query, {'input': variables})
                run.assert_not_called()

    def test_second_local_process_is_rejected(self):
        with sync.local_lock():
            with self.assertRaises(sync.SyncError):
                with sync.local_lock():
                    self.fail('Second process acquired lock')

    def test_cli_apply_guard_blocks_before_network(self):
        with patch('sys.argv', ['sync', '--apply', '--output-dir', '/unused']), \
             patch.dict(sync.os.environ, {'CODEMASTERY_DEPENDENCY_SYNC_ENABLED': ''}), \
             patch.object(sync, 'Client') as client, self.assertRaises(sync.SyncError):
            sync.main()
        client.assert_not_called()


class WorkflowTests(unittest.TestCase):
    def test_activation_default_and_trusted_event_guards(self):
        text = Path(__file__).with_name('sync-native-dependencies.yml').read_text()
        self.assertIn("cron: '17 * * * *'", text)
        self.assertIn('default: false', text)
        self.assertIn("github.ref == 'refs/heads/main'", text)
        self.assertIn("vars.CODEMASTERY_DEPENDENCY_SYNC_ENABLED == 'true'", text)
        self.assertIn('persist-credentials: false', text)
        self.assertIn('cancel-in-progress: false', text)
        self.assertNotIn('pull_request:', text)
        self.assertNotIn('repository_dispatch:', text)
        self.assertNotIn('upload-artifact', text)
        self.assertEqual(text.count('secrets.CODEMASTERY_PROJECT_SYNC_TOKEN'), 1)


if __name__ == '__main__':
    unittest.main()
